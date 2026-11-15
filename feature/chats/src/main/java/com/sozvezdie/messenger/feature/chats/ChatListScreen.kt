package com.sozvezdie.messenger.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sozvezdie.messenger.core.model.Chat
import com.sozvezdie.messenger.core.model.ChatType
import com.sozvezdie.messenger.core.model.GroupInvite
import com.sozvezdie.messenger.core.model.MessageType
import com.sozvezdie.messenger.core.ui.components.Avatar
import com.sozvezdie.messenger.core.ui.components.EmptyState
import com.sozvezdie.messenger.core.ui.components.LoadingScreen
import com.sozvezdie.messenger.core.ui.components.MainTab
import com.sozvezdie.messenger.core.ui.components.MessengerBottomBar
import com.sozvezdie.messenger.core.ui.util.formatChatTime
import com.sozvezdie.messenger.feature.motion.pressScale

/**
 * Главный экран мессенджера: список диалогов.
 * Показывает только реальные чаты, созданные пользователями.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    onChatClick: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSearchClick: () -> Unit,
) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val profile by viewModel.myProfile.collectAsStateWithLifecycle()
    val invites by viewModel.invites.collectAsStateWithLifecycle()
    val inviteBusy by viewModel.inviteBusy.collectAsStateWithLifecycle()
    val inviteError by viewModel.inviteError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(inviteError) {
        inviteError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearInviteError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Column {
                        Text(
                            text = "Созвездие",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = profile?.name ?: viewModel.myName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clickable(onClick = onProfileClick),
                    ) {
                        Avatar(
                            name = profile?.name ?: viewModel.myName,
                            photoUrl = profile?.photoUrl.orEmpty(),
                            size = 38.dp,
                        )
                    }
                },
            )
        },
        bottomBar = {
            MessengerBottomBar(
                current = MainTab.CHATS,
                onChats = {},
                onSearch = onSearchClick,
                onProfile = onProfileClick,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewChatClick,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Новый чат") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
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
                placeholder = { Text("Поиск по чатам") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )

            if (invites.isNotEmpty()) {
                Text(
                    text = "Приглашения",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(invites, key = { "invite_${it.chatId}" }) { invite ->
                        InviteCard(
                            invite = invite,
                            busy = inviteBusy,
                            onJoin = { viewModel.joinInvite(invite, onChatClick) },
                            onDecline = { viewModel.declineInvite(invite) },
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    loading && chats.isEmpty() -> LoadingScreen()
                    chats.isEmpty() && query.isBlank() -> EmptyState(
                        title = "Пока нет чатов",
                        subtitle = "Нажмите «Новый чат» и выберите собеседника. " +
                            "Для этого должен быть зарегистрирован хотя бы один другой аккаунт.",
                    )
                    chats.isEmpty() -> EmptyState(
                        title = "Ничего не нашли",
                        subtitle = "Попробуйте другое слово.",
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 4.dp,
                            bottom = 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(chats, key = { it.id }) { chat ->
                            ChatRow(
                                chat = chat,
                                myUid = viewModel.myUid,
                                onClick = { onChatClick(chat.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Карточка приглашения в группу.
 *
 * Показывает название, отправителя и короткую ссылку. Войти можно
 * кнопкой, а если приглашение не нужно, отклонить.
 */
@Composable
private fun InviteCard(
    invite: GroupInvite,
    busy: Boolean,
    onJoin: () -> Unit,
    onDecline: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(
                    name = invite.title.ifBlank { "Группа" },
                    photoUrl = invite.photoUrl,
                    size = 42.dp,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        text = invite.title.ifBlank { "Группа" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (invite.fromName.isBlank()) {
                            "Приглашение в группу"
                        } else {
                            "Приглашение от ${invite.fromName}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (invite.link.isNotBlank()) {
                        Text(
                            text = "@${invite.link}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDecline, enabled = !busy) {
                    Text("Отклонить")
                }
                Button(onClick = onJoin, enabled = !busy) {
                    Text("Присоединиться")
                }
            }
        }
    }
}

@Composable
private fun ChatRow(
    chat: Chat,
    myUid: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val unread = chat.unreadFor(myUid)
    val isGroup = chat.type == ChatType.GROUP

    Surface(
        color = if (unread) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                name = chat.displayTitle,
                photoUrl = if (isGroup) chat.photoUrl else chat.otherUserPhotoUrl,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chat.displayTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isGroup) {
                        Text(
                            text = "  ·  ${chat.memberCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = previewText(chat, myUid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatChatTime(chat.lastMessageAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (unread) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (unread) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
            }
        }
    }
}

/** Превью последнего сообщения: для фото, файлов и голосовых показываем подпись. */
private fun previewText(chat: Chat, myUid: String): String {
    val body = when (chat.lastMessageType) {
        MessageType.IMAGE -> "Фотография"
        MessageType.FILE -> chat.lastMessageText.ifBlank { "Файл" }
        MessageType.VOICE -> "Голосовое сообщение"
        MessageType.SYSTEM -> chat.lastMessageText
        MessageType.TEXT -> chat.lastMessageText
    }
    if (body.isBlank()) return "Начните переписку"

    return when {
        chat.lastMessageSenderId == myUid -> "Вы: $body"
        chat.type == ChatType.GROUP && chat.lastMessageSenderName.isNotBlank() ->
            "${chat.lastMessageSenderName}: $body"
        else -> body
    }
}
