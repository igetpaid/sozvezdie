package com.sozvezdie.messenger.feature.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sozvezdie.messenger.core.data.local.SettingsStore
import com.sozvezdie.messenger.core.data.repository.AuthRepository
import com.sozvezdie.messenger.core.data.repository.ChatRepository
import com.sozvezdie.messenger.core.data.repository.MediaRepository
import com.sozvezdie.messenger.core.data.repository.UserRepository
import com.sozvezdie.messenger.core.model.Chat
import com.sozvezdie.messenger.core.model.ChatRole
import com.sozvezdie.messenger.core.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Состояние экрана информации о группе. */
data class GroupInfoUiState(
    val chat: Chat? = null,
    val users: List<User> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val wallpaper: String = "",
    val error: String? = null,
)

/**
 * Участники группы, роли и настройки.
 *
 * Все действия сохраняются в Firebase сразу. Если что-то не получилось,
 * экран покажет понятную ошибку, а группа останется как была.
 */
class GroupInfoViewModel(
    private val chatId: String,
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val mediaRepository: MediaRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val myUid = authRepository.currentUserId.orEmpty()
    private val fallbackName = authRepository.currentUserName

    private val _state = MutableStateFlow(
        GroupInfoUiState(wallpaper = settingsStore.getWallpaper(chatId)),
    )
    val state: StateFlow<GroupInfoUiState> = _state.asStateFlow()

    private val myProfile: StateFlow<User?> = userRepository.observeProfile(myUid)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private val me: User
        get() = User(
            uid = myUid,
            email = "",
            name = myProfile.value?.name?.takeIf { it.isNotBlank() } ?: fallbackName,
            photoUrl = myProfile.value?.photoUrl.orEmpty(),
        )

    init {
        viewModelScope.launch {
            chatRepository.observeChat(chatId, myUid).collect { chat ->
                _state.update { it.copy(chat = chat, loading = false) }
            }
        }
        viewModelScope.launch {
            userRepository.observeUsers()
                .map { users -> users.filter { it.uid != myUid } }
                .collect { users -> _state.update { it.copy(users = users) } }
        }
    }

    /** Меняет название и, если выбрано новое фото, сначала загружает его. */
    fun updateGroup(title: String, photoUri: Uri?, onDone: () -> Unit) {
        if (_state.value.busy) return
        val chat = _state.value.chat ?: return

        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }

            var photoUrl = chat.photoUrl
            if (photoUri != null) {
                val uploaded = mediaRepository.uploadGroupPhoto(chatId, photoUri)
                if (uploaded.isFailure) {
                    _state.update {
                        it.copy(
                            busy = false,
                            error = uploaded.exceptionOrNull()?.message
                                ?: "Не удалось загрузить фото",
                        )
                    }
                    return@launch
                }
                photoUrl = uploaded.getOrNull().orEmpty()
            }

            chatRepository.updateGroup(
                chatId = chatId,
                myUid = myUid,
                myName = me.name,
                myPhotoUrl = me.photoUrl,
                title = title,
                photoUrl = photoUrl,
            )
                .onSuccess {
                    _state.update { it.copy(busy = false) }
                    onDone()
                }
                .onFailure { error ->
                    _state.update { it.copy(busy = false, error = error.message) }
                }
        }
    }

    /** Пересоздаёт ссылку группы. Старая ссылка после этого не работает. */
    fun regenerateLink(onDone: () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            chatRepository.regenerateGroupLink(chatId, myUid)
                .onSuccess { onDone() }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message) }
                }
            _state.update { it.copy(busy = false) }
        }
    }

    fun addMembers(users: List<User>, onDone: () -> Unit) {
        if (users.isEmpty() || _state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            // Права проверяют правила Firebase: добавлять могут только владелец и админ.
            chatRepository.addMembers(
                chatId = chatId,
                myUid = myUid,
                myName = me.name,
                myPhotoUrl = me.photoUrl,
                users = users,
            )
                .onSuccess {
                    _state.update { it.copy(busy = false) }
                    onDone()
                }
                .onFailure { error ->
                    _state.update { it.copy(busy = false, error = error.message) }
                }
        }
    }

    fun removeMember(user: User) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            chatRepository.removeMember(chatId, myUid, me.name, me.photoUrl, user)
                .onFailure { error -> _state.update { it.copy(error = error.message) } }
            _state.update { it.copy(busy = false) }
        }
    }

    fun setAdmin(user: User, admin: Boolean) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            chatRepository.setAdmin(chatId, myUid, me.name, me.photoUrl, user, admin)
                .onFailure { error -> _state.update { it.copy(error = error.message) } }
            _state.update { it.copy(busy = false) }
        }
    }

    fun transferOwnership(user: User) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            chatRepository.transferOwnership(chatId, myUid, me.name, me.photoUrl, user)
                .onFailure { error -> _state.update { it.copy(error = error.message) } }
            _state.update { it.copy(busy = false) }
        }
    }

    fun leaveGroup(onDone: () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            chatRepository.leaveGroup(chatId, myUid, me.name, me.photoUrl)
                .onSuccess { onDone() }
                .onFailure { error -> _state.update { it.copy(error = error.message) } }
            _state.update { it.copy(busy = false) }
        }
    }

    fun deleteGroup(onDone: () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            chatRepository.deleteGroup(chatId, myUid)
                .onSuccess { onDone() }
                .onFailure { error -> _state.update { it.copy(error = error.message) } }
            _state.update { it.copy(busy = false) }
        }
    }

    /** Запоминает готовые обои группы. */
    fun setWallpaper(spec: String) {
        settingsStore.setWallpaper(chatId, spec)
        _state.update { it.copy(wallpaper = spec) }
    }

    /** Сохраняет выбранное фото как обои этой группы. */
    fun saveCustomWallpaper(uri: Uri) {
        viewModelScope.launch {
            mediaRepository.saveWallpaper(chatId, uri)
                .onSuccess { path -> setWallpaper("file:$path") }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = error.message ?: "Не удалось сохранить обои")
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    companion object {
        fun factory(
            chatId: String,
            authRepository: AuthRepository,
            chatRepository: ChatRepository,
            userRepository: UserRepository,
            mediaRepository: MediaRepository,
            settingsStore: SettingsStore,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GroupInfoViewModel(
                    chatId = chatId,
                    authRepository = authRepository,
                    chatRepository = chatRepository,
                    userRepository = userRepository,
                    mediaRepository = mediaRepository,
                    settingsStore = settingsStore,
                )
            }
        }
    }
}
