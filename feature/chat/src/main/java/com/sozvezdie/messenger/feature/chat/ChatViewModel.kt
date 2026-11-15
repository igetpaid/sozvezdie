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
import com.sozvezdie.messenger.core.data.repository.MessageRepository
import com.sozvezdie.messenger.core.data.repository.UserRepository
import com.sozvezdie.messenger.core.model.Chat
import com.sozvezdie.messenger.core.model.ChatType
import com.sozvezdie.messenger.core.model.Message
import com.sozvezdie.messenger.core.model.Presence
import com.sozvezdie.messenger.core.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

/** Состояние экрана переписки. */
data class ChatUiState(
    val chat: Chat? = null,
    val messages: List<Message> = emptyList(),
    val typingNames: List<String> = emptyList(),
    val presence: Presence = Presence(),
    val myProfile: User? = null,
    val otherProfile: User? = null,
    val loading: Boolean = true,
    val uploading: Boolean = false,
    // Файл, который пользователь попросил открыть. Экран покажет его и очистит.
    val fileToOpen: File? = null,
    val wallpaper: String = "",
    val draft: String = "",
    val replyTo: Message? = null,
    val error: String? = null,
)

/**
 * Переписка одного чата.
 *
 * ViewModel слушает сообщения, данные чата, «печатает» и присутствие
 * собеседника. Всё складывается в одно состояние, а экран рисует его.
 */
class ChatViewModel(
    private val chatId: String,
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val mediaRepository: MediaRepository,
    private val userRepository: UserRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    // id текущего пользователя нужен экрану, чтобы отличать свои сообщения.
    val currentUserId: String = authRepository.currentUserId.orEmpty()
    private val myUid = currentUserId
    private val fallbackName = authRepository.currentUserName

    private val _state = MutableStateFlow(
        ChatUiState(
            wallpaper = settingsStore.getWallpaper(chatId),
            draft = settingsStore.getDraft(chatId),
        ),
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    // Время последней отметки «прочитано», чтобы не писать в базу на каждое сообщение.
    private var lastMarkedAt = 0L
    private var typingJob: Job? = null
    private var typingSent = false
    private var presenceUid: String? = null
    private var presenceJob: Job? = null

    private val me: User
        get() {
            val profile = _state.value.myProfile
            return User(
                uid = myUid,
                email = profile?.email.orEmpty(),
                name = profile?.name?.takeIf { it.isNotBlank() } ?: fallbackName,
                username = profile?.username.orEmpty(),
                photoUrl = profile?.photoUrl.orEmpty(),
                about = profile?.about.orEmpty(),
            )
        }

    init {
        observeMessages()
        observeChat()
        observeTyping()
        observeMyProfile()
    }

    private fun observeMessages() {
        viewModelScope.launch {
            messageRepository.observeMessages(chatId).collect { messages ->
                _state.update { it.copy(messages = messages, loading = false) }
                markReadIfNeeded(messages)
            }
        }
    }

    private fun observeChat() {
        viewModelScope.launch {
            chatRepository.observeChat(chatId, myUid).collect { chat ->
                _state.update { it.copy(chat = chat) }
                chat ?: return@collect

                // Присутствие и профиль слушаем только у личного собеседника.
                if (chat.type == ChatType.DIRECT && chat.otherUserId != presenceUid) {
                    presenceUid = chat.otherUserId
                    observePresence(chat.otherUserId)
                    observeOtherProfile(chat.otherUserId)
                }
            }
        }
    }

    private fun observePresence(uid: String) {
        presenceJob?.cancel()
        if (uid.isBlank()) return
        presenceJob = viewModelScope.launch {
            userRepository.observePresence(uid).collect { presence ->
                _state.update { it.copy(presence = presence) }
            }
        }
    }

    private fun observeOtherProfile(uid: String) {
        if (uid.isBlank()) return
        viewModelScope.launch {
            userRepository.observeProfile(uid).collect { user ->
                _state.update { it.copy(otherProfile = user) }
            }
        }
    }

    private fun observeTyping() {
        viewModelScope.launch {
            chatRepository.observeTyping(chatId, myUid).collect { names ->
                _state.update { it.copy(typingNames = names) }
            }
        }
    }

    private fun observeMyProfile() {
        viewModelScope.launch {
            userRepository.observeProfile(myUid).collect { profile ->
                _state.update { it.copy(myProfile = profile) }
            }
        }
    }

    /** После возврата с экрана обоев подхватываем новые настройки. */
    fun reloadLocalSettings() {
        _state.update {
            it.copy(
                wallpaper = settingsStore.getWallpaper(chatId),
                draft = settingsStore.getDraft(chatId),
            )
        }
    }

    /**
     * Отмечает чат прочитанным, когда пришло новое чужое сообщение.
     * Время записи сравниваем, чтобы не отправлять одинаковые запросы.
     */
    private fun markReadIfNeeded(messages: List<Message>) {
        val latest = messages.lastOrNull() ?: return
        if (latest.senderId == myUid) return
        if (latest.createdAt <= lastMarkedAt) return

        lastMarkedAt = latest.createdAt
        viewModelScope.launch {
            chatRepository.markRead(chatId, myUid)
        }
    }

    fun sendText(text: String) {
        val body = text.trim()
        if (body.isEmpty()) return

        val reply = _state.value.replyTo
        viewModelScope.launch {
            clearTyping()
            messageRepository.sendText(chatId, me, body, reply)
                .onSuccess {
                    settingsStore.setDraft(chatId, "")
                    _state.update { it.copy(replyTo = null, draft = "") }
                }
                .onFailure { error -> showError(error.message) }
        }
    }

    fun sendImage(uri: Uri) {
        val reply = _state.value.replyTo
        viewModelScope.launch {
            // Сначала загружаем файл, потом пишем сообщение: иначе в чате
            // осталась бы пустая карточка без картинки.
            _state.update { it.copy(uploading = true, error = null) }
            mediaRepository.uploadImage(chatId, uri)
                .onSuccess { url ->
                    messageRepository.sendImage(chatId, me, url, reply)
                        .onSuccess { _state.update { it.copy(replyTo = null) } }
                        .onFailure { error -> showError(error.message) }
                }
                .onFailure { error -> showError(error.message) }
            _state.update { it.copy(uploading = false) }
        }
    }

    fun sendFile(uri: Uri) {
        val reply = _state.value.replyTo
        viewModelScope.launch {
            _state.update { it.copy(uploading = true, error = null) }
            mediaRepository.uploadFile(chatId, uri)
                .onSuccess { uploaded ->
                    messageRepository.sendFile(
                        chatId = chatId,
                        sender = me,
                        mediaUrl = uploaded.url,
                        fileName = uploaded.fileName,
                        fileSize = uploaded.size,
                        reply = reply,
                    )
                        .onSuccess { _state.update { it.copy(replyTo = null) } }
                        .onFailure { error -> showError(error.message) }
                }
                .onFailure { error -> showError(error.message) }
            _state.update { it.copy(uploading = false) }
        }
    }

    fun sendVoice(file: File, durationMs: Long) {
        val reply = _state.value.replyTo
        viewModelScope.launch {
            _state.update { it.copy(uploading = true, error = null) }
            mediaRepository.uploadVoice(chatId, file)
                .onSuccess { url ->
                    messageRepository.sendVoice(
                        chatId = chatId,
                        sender = me,
                        mediaUrl = url,
                        durationMs = durationMs,
                        waveform = makeWaveform(),
                        reply = reply,
                    )
                        .onSuccess {
                            _state.update { it.copy(replyTo = null) }
                            file.delete()
                        }
                        .onFailure { error -> showError(error.message) }
                }
                .onFailure { error -> showError(error.message) }
            _state.update { it.copy(uploading = false) }
        }
    }

    /** Рисуем простую волну: набор случайных столбиков разной высоты. */
    private fun makeWaveform(): String =
        (1..32).joinToString(",") { Random.nextInt(2, 10).toString() }

    fun setReply(message: Message) {
        _state.update { it.copy(replyTo = message) }
    }

    fun clearReply() {
        _state.update { it.copy(replyTo = null) }
    }

    fun toggleReaction(message: Message, emoji: String) {
        viewModelScope.launch {
            messageRepository.toggleReaction(chatId, message.id, myUid, emoji)
                .onFailure { error -> showError(error.message) }
        }
    }

    /** Готовит файл к открытию: сохраняет во временную папку приложения. */
    fun openFile(url: String, fileName: String) {
        viewModelScope.launch {
            mediaRepository.downloadFile(url, fileName)
                .onSuccess { file -> _state.update { it.copy(fileToOpen = file) } }
                .onFailure { error -> showError(error.message ?: "Не удалось открыть файл") }
        }
    }

    fun onFileOpened() {
        _state.update { it.copy(fileToOpen = null) }
    }

    fun setWallpaper(spec: String) {
        settingsStore.setWallpaper(chatId, spec)
        _state.update { it.copy(wallpaper = spec) }
    }

    /** Сохраняет выбранное фото как обои именно этого чата. */
    fun saveCustomWallpaper(uri: Uri) {
        viewModelScope.launch {
            mediaRepository.saveWallpaper(chatId, uri)
                .onSuccess { path -> setWallpaper("file:$path") }
                .onFailure { error -> showError(error.message ?: "Не удалось сохранить обои") }
        }
    }

    /**
     * Пользователь печатает: ставим метку в Firebase и снимаем её
     * через две секунды после последнего нажатия клавиши.
     */
    fun onTextChanged(text: String) {
        settingsStore.setDraft(chatId, text)
        _state.update { it.copy(draft = text) }

        if (text.isNotEmpty() && !typingSent) {
            typingSent = true
            viewModelScope.launch {
                chatRepository.setTyping(chatId, myUid, me.name)
            }
        }

        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            delay(2_000)
            clearTyping()
        }

        if (text.isEmpty()) {
            typingJob?.cancel()
            viewModelScope.launch { clearTyping() }
        }
    }

    private suspend fun clearTyping() {
        if (!typingSent) return
        typingSent = false
        chatRepository.clearTyping(chatId, myUid)
    }

    fun reportError(message: String) {
        showError(message)
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /** Удаляет своё сообщение. */
    fun deleteMessage(message: Message) {
        viewModelScope.launch {
            messageRepository.deleteMessage(chatId, message.id)
                .onFailure { error -> showError(error.message ?: "Не удалось удалить сообщение") }
        }
    }

    private fun showError(message: String?) {
        _state.update { it.copy(error = message ?: "Не удалось отправить сообщение") }
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope уже отменён, поэтому метку «печатает» снимаем
        // в отдельной короткоживущей корутине.
        if (typingSent) {
            CoroutineScope(Dispatchers.IO).launch { clearTyping() }
        }
    }

    companion object {
        fun factory(
            chatId: String,
            authRepository: AuthRepository,
            chatRepository: ChatRepository,
            messageRepository: MessageRepository,
            mediaRepository: MediaRepository,
            userRepository: UserRepository,
            settingsStore: SettingsStore,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    chatId = chatId,
                    authRepository = authRepository,
                    chatRepository = chatRepository,
                    messageRepository = messageRepository,
                    mediaRepository = mediaRepository,
                    userRepository = userRepository,
                    settingsStore = settingsStore,
                )
            }
        }
    }
}
