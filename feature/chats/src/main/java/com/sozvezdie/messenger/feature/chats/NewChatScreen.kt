package com.sozvezdie.messenger.feature.chats

import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sozvezdie.messenger.core.data.repository.CreatedGroup
import com.sozvezdie.messenger.core.model.User
import com.sozvezdie.messenger.core.ui.components.Avatar
import com.sozvezdie.messenger.core.ui.components.EmptyState
import com.sozvezdie.messenger.feature.media.rememberPhotoPicker

/**
 * Экран «Новый чат»: поиск по имени и выбор собеседника,
 * а также создание группы с названием и фотографией.
 *
 * Здесь нет выдуманных контактов: только аккаунты, которые
 * действительно зарегистрированы в Firebase.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(
    viewModel: NewChatViewModel,
    onBack: () -> Unit,
    onChatOpened: (String) -> Unit,
) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    var groupPhoto by remember { mutableStateOf<Uri?>(null) }
    var showJoinDialog by remember { mutableStateOf(false) }
    val pickPhoto = rememberPhotoPicker { uri -> groupPhoto = uri }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Новый чат", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.mode == NewChatMode.DIRECT,
                    onClick = { viewModel.setMode(NewChatMode.DIRECT) },
                    label = { Text("Личный чат") },
                )
                FilterChip(
                    selected = state.mode == NewChatMode.GROUP,
                    onClick = { viewModel.setMode(NewChatMode.GROUP) },
                    label = { Text("Группа") },
                )
            }

            // Войти в группу можно в любом режиме: это отдельное действие.
            OutlinedButton(
                onClick = {
                    viewModel.clearError()
                    showJoinDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Default.Link, contentDescription = null)
                Text(
                    text = "Присоединиться по ссылке",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            if (state.mode == NewChatMode.GROUP) {
                OutlinedTextField(
                    value = state.groupTitle,
                    onValueChange = viewModel::onGroupTitleChange,
                    label = { Text("Название группы") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = pickPhoto)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (groupPhoto != null) {
                        AsyncImage(
                            model = groupPhoto,
                            contentDescription = "Фото группы",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .height(52.dp)
                                .clip(RoundedCornerShape(14.dp)),
                        )
                    } else {
                        Avatar(
                            name = state.groupTitle.ifBlank { "Группа" },
                            size = 52.dp,
                            colorIndex = state.groupColorIndex,
                        )
                    }
                    Text(
                        text = "Фото группы (необязательно)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Выбрано: ${state.selectedUids.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { viewModel.createGroup(groupPhoto) },
                        enabled = !state.loading,
                    ) {
                        Text("Создать")
                    }
                }
            }

            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Имя, ник или email") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (state.error != null) {
                Text(
                    text = state.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    users.isEmpty() && query.isBlank() -> EmptyState(
                        title = "Других пользователей пока нет",
                        subtitle = "Зарегистрируйте второй аккаунт в этом же приложении " +
                            "на другом устройстве или эмуляторе — он появится здесь.",
                    )

                    users.isEmpty() -> EmptyState(
                        title = "Никого не нашли",
                        subtitle = "Попробуйте другое имя, ник или email.",
                    )

                    else -> LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(users, key = { it.uid }) { user ->
                            val selected = user.uid in state.selectedUids
                            UserRow(
                                user = user,
                                showCheckbox = state.mode == NewChatMode.GROUP,
                                selected = selected,
                                onClick = {
                                    if (state.mode == NewChatMode.GROUP) {
                                        viewModel.toggleMember(user.uid)
                                    } else {
                                        viewModel.openChat(user, onChatOpened)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showJoinDialog) {
        JoinGroupDialog(
            link = state.joinLink,
            loading = state.joinLoading,
            error = state.error,
            onLinkChange = viewModel::onJoinLinkChange,
            onDismiss = {
                if (!state.joinLoading) {
                    showJoinDialog = false
                    viewModel.clearError()
                }
            },
            onJoin = {
                viewModel.joinByLink { chatId ->
                    showJoinDialog = false
                    onChatOpened(chatId)
                }
            },
        )
    }

    state.createdGroup?.let { created ->
        GroupCreatedDialog(
            created = created,
            onOpenChat = {
                viewModel.dismissCreatedGroup()
                onChatOpened(created.chatId)
            },
            onDismiss = viewModel::dismissCreatedGroup,
        )
    }
}

/**
 * Окно входа в группу по ссылке.
 *
 * Сюда можно вставить `@group1`, код без собачки или полную ссылку.
 */
@Composable
private fun JoinGroupDialog(
    link: String,
    loading: Boolean,
    error: String?,
    onLinkChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onJoin: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Присоединиться к группе") },
        text = {
            Column {
                OutlinedTextField(
                    value = link,
                    onValueChange = onLinkChange,
                    label = { Text("Ссылка или код") },
                    placeholder = { Text("@group1") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onJoin,
                enabled = link.isNotBlank() && !loading,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Присоединиться")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text("Отмена")
            }
        },
    )
}

/**
 * Окно после создания группы.
 *
 * Здесь видно ссылку и людей, которым уже отправлено приглашение.
 * Нажатие на ссылку открывает группу, потому что создатель уже её участник.
 */
@Composable
private fun GroupCreatedDialog(
    created: CreatedGroup,
    onOpenChat: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text("Группа создана") },
        text = {
            Column {
                Text(
                    text = "«${created.title}»",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Ссылка-приглашение:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "@${created.link}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenChat() },
                    )
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString("@${created.link}"))
                            copied = true
                        },
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Копировать ссылку",
                        )
                    }
                }

                if (created.invitedNames.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Приглашение отправлено:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(
                        modifier = Modifier
                            .heightIn(max = 120.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = created.invitedNames.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (copied) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Ссылка скопирована",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onOpenChat) {
                Text("Перейти в группу")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Готово")
            }
        },
    )
}

@Composable
private fun UserRow(
    user: User,
    showCheckbox: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                name = user.name,
                photoUrl = user.photoUrl,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (user.username.isBlank()) user.email else user.usernameLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showCheckbox) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            }
        }
    }
}
