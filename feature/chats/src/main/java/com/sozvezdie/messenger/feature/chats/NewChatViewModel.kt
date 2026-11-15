package com.sozvezdie.messenger.feature.chats

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sozvezdie.messenger.core.data.repository.AuthRepository
import com.sozvezdie.messenger.core.data.repository.ChatRepository
import com.sozvezdie.messenger.core.data.repository.CreatedGroup
import com.sozvezdie.messenger.core.data.repository.MediaRepository
import com.sozvezdie.messenger.core.data.repository.UserRepository
import com.sozvezdie.messenger.core.model.User
import com.sozvezdie.messenger.core.model.normalizeSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Что создаём: личный чат или группу. */
enum class NewChatMode {
    DIRECT,
    GROUP,
}

/** Состояние экрана выбора собеседника. */
data class NewChatUiState(
    val mode: NewChatMode = NewChatMode.DIRECT,
    val loading: Boolean = false,
    val error: String? = null,
    val groupTitle: String = "",
    // Цвет будущей группы выбираем один раз при входе в режим «Группа».
    val groupColorIndex: Int = 0,
    val selectedUids: Set<String> = emptySet(),
    // Вход по ссылке.
    val joinLink: String = "",
    val joinLoading: Boolean = false,
    // Окно приглашения показываем уже после создания группы.
    val createdGroup: CreatedGroup? = null,
)

/**
 * Выбор пользователя для нового диалога и создание группы.
 *
 * Список берётся из узла users в Firebase, то есть это реальные
 * зарегистрированные аккаунты. Себя из списка убираем.
 */
class NewChatViewModel(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    userRepository: UserRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val myUid = authRepository.currentUserId.orEmpty()
    private val myName = authRepository.currentUserName

    private val _state = MutableStateFlow(NewChatUiState())
    val state: StateFlow<NewChatUiState> = _state.asStateFlow()

    // Поиск по имени, нику или email: простой фильтр поверх потока пользователей.
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val myProfile: StateFlow<User?> = userRepository.observeProfile(myUid)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private val allUsers: StateFlow<List<User>> = userRepository.observeUsers()
        .map { users -> users.filter { it.uid != myUid } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val users: StateFlow<List<User>> = combine(allUsers, _query) { users, query ->
        val text = normalizeSearch(query)
        if (text.isEmpty()) {
            // По умолчанию никого не показываем: пользователь должен ввести запрос.
            emptyList()
        } else {
            users.filter {
                normalizeSearch(it.name).contains(text) ||
                    normalizeSearch(it.email).contains(text) ||
                    normalizeSearch(it.username).contains(text)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun setMode(mode: NewChatMode) {
        _state.update { current ->
            // Новый цвет берём только при входе в режим группы.
            // Пока пользователь печатает название, индекс не меняется.
            val colorIndex = if (mode == NewChatMode.GROUP && current.mode != NewChatMode.GROUP) {
                Random.nextInt(0, 1000)
            } else {
                current.groupColorIndex
            }
            current.copy(mode = mode, groupColorIndex = colorIndex, error = null)
        }
    }

    fun onGroupTitleChange(value: String) {
        // В Firebase у названия ограничение 60 символов, поэтому длиннее не вводим.
        _state.update { it.copy(groupTitle = value.take(60), error = null) }
    }

    fun toggleMember(uid: String) {
        _state.update { state ->
            val selected = state.selectedUids.toMutableSet()
            if (!selected.add(uid)) selected.remove(uid)
            state.copy(selectedUids = selected, error = null)
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Открывает чат с выбранным пользователем.
     * onOpened получает id чата, по нему приложение перейдёт к переписке.
     */
    fun openChat(other: User, onOpened: (String) -> Unit) {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val me = myProfile.value
            chatRepository.openChat(
                myUid = myUid,
                myName = me?.name ?: myName,
                myPhotoUrl = me?.photoUrl.orEmpty(),
                other = other,
            )
                .onSuccess { chatId ->
                    _state.update { it.copy(loading = false) }
                    onOpened(chatId)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(loading = false, error = error.message ?: "Не удалось открыть чат")
                    }
                }
        }
    }

    /** Создаёт группу, отправляет приглашения и открывает окно приглашения. */
    fun createGroup(photoUri: Uri?) {
        val current = _state.value
        if (current.loading) return

        val title = current.groupTitle.trim()
        if (title.isEmpty()) {
            _state.update { it.copy(error = "Введите название группы") }
            return
        }
        if (current.selectedUids.isEmpty()) {
            _state.update { it.copy(error = "Выберите хотя бы одного участника") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }

            val members = allUsers.value.filter { it.uid in current.selectedUids }
            val me = myProfile.value

            var photoUrl = ""
            if (photoUri != null) {
                val uploaded = mediaRepository.uploadGroupPhoto("new", photoUri)
                if (uploaded.isFailure) {
                    _state.update {
                        it.copy(
                            loading = false,
                            error = uploaded.exceptionOrNull()?.message
                                ?: "Не удалось загрузить фото группы",
                        )
                    }
                    return@launch
                }
                photoUrl = uploaded.getOrNull().orEmpty()
            }

            chatRepository.createGroup(
                myUid = myUid,
                myName = me?.name ?: myName,
                myPhotoUrl = me?.photoUrl.orEmpty(),
                title = title,
                photoUrl = photoUrl,
                members = members,
            )
                .onSuccess { created ->
                    // Форму очищаем, чтобы случайно не создать вторую такую же группу.
                    _state.update {
                        it.copy(
                            loading = false,
                            groupTitle = "",
                            selectedUids = emptySet(),
                            createdGroup = created,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(loading = false, error = error.message ?: "Не удалось создать группу")
                    }
                }
        }
    }

    /** Закрывает окно приглашения. Сама группа уже создана. */
    fun dismissCreatedGroup() {
        _state.update { it.copy(createdGroup = null) }
    }

    fun onJoinLinkChange(value: String) {
        _state.update { it.copy(joinLink = value, error = null) }
    }

    /**
     * Входит в группу по ссылке или коду.
     *
     * Если человек уже участник, сервер просто вернёт тот же чат,
     * и приложение откроет его.
     */
    fun joinByLink(onJoined: (String) -> Unit) {
        val current = _state.value
        if (current.joinLoading) return

        val link = current.joinLink.trim()
        if (link.isEmpty()) {
            _state.update { it.copy(error = "Введите ссылку или код группы") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(joinLoading = true, error = null) }
            val me = myProfile.value
            chatRepository.joinGroupByLink(
                input = link,
                myUid = myUid,
                myName = me?.name ?: myName,
                myPhotoUrl = me?.photoUrl.orEmpty(),
            )
                .onSuccess { chatId ->
                    _state.update { it.copy(joinLoading = false, joinLink = "") }
                    onJoined(chatId)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            joinLoading = false,
                            error = error.message ?: "Не удалось присоединиться к группе",
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(
            authRepository: AuthRepository,
            chatRepository: ChatRepository,
            userRepository: UserRepository,
            mediaRepository: MediaRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NewChatViewModel(
                    authRepository = authRepository,
                    chatRepository = chatRepository,
                    userRepository = userRepository,
                    mediaRepository = mediaRepository,
                )
            }
        }
    }
}
