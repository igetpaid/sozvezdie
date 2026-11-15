package com.sozvezdie.messenger.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sozvezdie.messenger.core.data.repository.AuthRepository
import com.sozvezdie.messenger.core.data.repository.ChatRepository
import com.sozvezdie.messenger.core.data.repository.MessageRepository
import com.sozvezdie.messenger.core.model.Chat
import com.sozvezdie.messenger.core.model.FoundMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Поиск по чатам и по сохранённой истории сообщений.
 *
 * Ищем только на телефоне, в данных Room: это быстро и работает
 * даже без интернета. Запрос немного ждём, чтобы не искать
 * на каждую букву.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    authRepository: AuthRepository,
    chatRepository: ChatRepository,
    messageRepository: MessageRepository,
) : ViewModel() {

    val myUid: String = authRepository.currentUserId.orEmpty()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // debounce ждёт паузу в наборе, flatMapLatest отменяет прошлый поиск.
    val chats: StateFlow<List<Chat>> = _query
        .debounce(250)
        .flatMapLatest { text ->
            if (text.isBlank()) flowOf(emptyList()) else chatRepository.searchChats(text)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val messages: StateFlow<List<FoundMessage>> = _query
        .debounce(250)
        .flatMapLatest { text ->
            if (text.isBlank()) flowOf(emptyList()) else messageRepository.searchMessages(text)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun onQueryChange(value: String) {
        _query.value = value
    }

    companion object {
        fun factory(
            authRepository: AuthRepository,
            chatRepository: ChatRepository,
            messageRepository: MessageRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(authRepository, chatRepository, messageRepository)
            }
        }
    }
}
