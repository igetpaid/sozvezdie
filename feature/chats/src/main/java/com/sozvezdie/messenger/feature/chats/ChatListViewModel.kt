package com.sozvezdie.messenger.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sozvezdie.messenger.core.data.repository.AuthRepository
import com.sozvezdie.messenger.core.data.repository.ChatRepository
import com.sozvezdie.messenger.core.data.repository.UserRepository
import com.sozvezdie.messenger.core.model.Chat
import com.sozvezdie.messenger.core.model.GroupInvite
import com.sozvezdie.messenger.core.model.User
import com.sozvezdie.messenger.core.model.normalizeSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Список чатов текущего пользователя.
 *
 * Репозиторий уже отдаёт готовый поток из Room, а ViewModel только
 * превращает его в состояние для Compose, хранит профиль и поиск.
 */
class ChatListViewModel(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    userRepository: UserRepository,
) : ViewModel() {

    val myUid: String = authRepository.currentUserId.orEmpty()
    val myName: String = authRepository.currentUserName

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // Приглашения в группы показываем отдельно над списком чатов.
    val invites: StateFlow<List<GroupInvite>> = chatRepository.observeGroupInvites(myUid)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _inviteBusy = MutableStateFlow(false)
    val inviteBusy: StateFlow<Boolean> = _inviteBusy

    private val _inviteError = MutableStateFlow<String?>(null)
    val inviteError: StateFlow<String?> = _inviteError

    val myProfile: StateFlow<User?> = userRepository.observeProfile(myUid)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private val allChats: StateFlow<List<Chat>> = chatRepository.observeChats(myUid)
        .onStart { _loading.value = true }
        .onEach { _loading.value = false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    // Поиск по названию, имени собеседника и последнему сообщению.
    val chats: StateFlow<List<Chat>> = combine(allChats, _query) { chats, query ->
        val text = normalizeSearch(query)
        if (text.isEmpty()) {
            chats
        } else {
            chats.filter { chat ->
                normalizeSearch(
                    "${chat.displayTitle} ${chat.lastMessageText} ${chat.searchText}",
                ).contains(text)
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

    /** Принимает приглашение и открывает группу. */
    fun joinInvite(invite: GroupInvite, onJoined: (String) -> Unit) {
        if (_inviteBusy.value) return
        viewModelScope.launch {
            _inviteBusy.value = true
            _inviteError.value = null
            val me = myProfile.value
            chatRepository.joinGroupByInvite(
                invite = invite,
                myUid = myUid,
                myName = me?.name ?: myName,
                myPhotoUrl = me?.photoUrl.orEmpty(),
            )
                .onSuccess { chatId -> onJoined(chatId) }
                .onFailure { error ->
                    _inviteError.value = error.message ?: "Не удалось присоединиться к группе"
                }
            _inviteBusy.value = false
        }
    }

    fun declineInvite(invite: GroupInvite) {
        if (_inviteBusy.value) return
        viewModelScope.launch {
            _inviteBusy.value = true
            _inviteError.value = null
            chatRepository.declineGroupInvite(myUid, invite.chatId)
                .onFailure { error ->
                    _inviteError.value = error.message ?: "Не удалось отклонить приглашение"
                }
            _inviteBusy.value = false
        }
    }

    fun clearInviteError() {
        _inviteError.value = null
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    companion object {
        fun factory(
            authRepository: AuthRepository,
            chatRepository: ChatRepository,
            userRepository: UserRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatListViewModel(authRepository, chatRepository, userRepository)
            }
        }
    }
}
