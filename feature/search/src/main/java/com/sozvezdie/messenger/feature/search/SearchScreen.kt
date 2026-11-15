package com.sozvezdie.messenger.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sozvezdie.messenger.core.model.Chat
import com.sozvezdie.messenger.core.model.FoundMessage
import com.sozvezdie.messenger.core.ui.components.Avatar
import com.sozvezdie.messenger.core.ui.components.EmptyState
import com.sozvezdie.messenger.core.ui.components.MainTab
import com.sozvezdie.messenger.core.ui.components.MessengerBottomBar
import com.sozvezdie.messenger.core.ui.util.formatChatTime

/**
 * Поиск по названиям чатов и по тексту сохранённых сообщений.
 * Найденный чат или сообщение открывает переписку.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onChatClick: (String) -> Unit,
    onChats: () -> Unit,
    onProfile: () -> Unit,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Поиск", fontWeight = FontWeight.SemiBold) },
            )
        },
        bottomBar = {
            MessengerBottomBar(
                current = MainTab.SEARCH,
                onChats = onChats,
                onSearch = {},
                onProfile = onProfile,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Слово из сообщения или название чата") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    query.isBlank() -> EmptyState(
                        title = "Что будем искать?",
                        subtitle = "Введите имя человека, название группы " +
                            "или слово из переписки. Поиск идёт по сохранённым чатам.",
                    )

                    chats.isEmpty() && messages.isEmpty() -> EmptyState(
                        title = "Ничего не нашли",
                        subtitle = "Попробуйте другое слово или откройте чат, " +
                            "чтобы его история сохранилась на телефоне.",
                    )

                    else -> LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (chats.isNotEmpty()) {
                            item {
                                SectionTitle("Чаты")
                            }
                            items(chats, key = { "chat_${it.id}" }) { chat ->
                                ChatResultRow(
                                    chat = chat,
                                    onClick = { onChatClick(chat.id) },
                                )
                            }
                        }

                        if (messages.isNotEmpty()) {
                            item {
                                SectionTitle("Сообщения")
                            }
                            items(messages, key = { "message_${it.message.id}" }) { found ->
                                MessageResultRow(
                                    found = found,
                                    onClick = { onChatClick(found.chatId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun ChatResultRow(chat: Chat, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                name = chat.displayTitle,
                photoUrl = if (chat.type == com.sozvezdie.messenger.core.model.ChatType.GROUP) {
                    chat.photoUrl
                } else {
                    chat.otherUserPhotoUrl
                },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = chat.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = chat.lastMessageText.ifBlank { "Нет сообщений" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatChatTime(chat.lastMessageAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageResultRow(found: FoundMessage, onClick: () -> Unit) {
    val message = found.message
    val preview = when (message.type) {
        com.sozvezdie.messenger.core.model.MessageType.IMAGE -> "Фотография"
        com.sozvezdie.messenger.core.model.MessageType.FILE ->
            message.fileName.ifBlank { "Файл" }
        com.sozvezdie.messenger.core.model.MessageType.VOICE -> "Голосовое сообщение"
        else -> message.text
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = found.chatTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatChatTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (message.senderName.isNotBlank()) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
