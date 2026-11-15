package com.sozvezdie.messenger.feature.chat

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sozvezdie.messenger.core.model.Chat
import com.sozvezdie.messenger.core.model.ChatRole
import com.sozvezdie.messenger.core.model.User
import com.sozvezdie.messenger.core.model.normalizeSearch
import com.sozvezdie.messenger.core.ui.components.Avatar
import com.sozvezdie.messenger.core.ui.components.LoadingScreen
import com.sozvezdie.messenger.feature.media.rememberPhotoPicker
import kotlinx.coroutines.launch

/**
 * Информация о группе: фото, название, участники и роли.
 *
 * Владелец может менять группу, назначать администраторов и удалять её.
 * Администратор может добавлять и удалять обычных участников.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    viewModel: GroupInfoViewModel,
    onBack: () -> Unit,
    onLeft: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chat = state.chat
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var showEdit by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showWallpaper by remember { mutableStateOf(false) }
    var showLeave by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showRegenerateLink by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<User?>(null) }
    var memberToTransfer by remember { mutableStateOf<User?>(null) }

    val pickWallpaper = rememberPhotoPicker { uri ->
        viewModel.saveCustomWallpaper(uri)
        showWallpaper = false
    }

    LaunchedEffect(state.error) {
        state.error?.let { message ->
            snackbarHost.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("О группе", fontWeight = FontWeight.SemiBold) },
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
        if (chat == null) {
            Box(modifier = Modifier.padding(padding)) {
                LoadingScreen()
            }
            return@Scaffold
        }

        val canManage = chat.myRole == ChatRole.OWNER || chat.myRole == ChatRole.ADMIN
        val isOwner = chat.myRole == ChatRole.OWNER
        val members = chat.members.map { uid ->
            User(
                uid = uid,
                email = "",
                name = chat.memberNames[uid]?.ifBlank { "Участник" } ?: "Участник",
                photoUrl = chat.memberPhotos[uid].orEmpty(),
            )
        }.sortedWith(
            compareBy<User> { member ->
                when {
                    member.uid == chat.ownerId -> 0
                    member.uid in chat.admins -> 1
                    else -> 2
                }
            }.thenBy { it.name.lowercase() },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Avatar(
                            name = chat.displayTitle,
                            photoUrl = chat.photoUrl,
                            size = 96.dp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = chat.displayTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${chat.memberCount} участников",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (canManage) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = { showEdit = true }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text("Изменить", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    if (canManage) {
                        // Добавление открывает поиск по нику, как при создании группы.
                        Button(
                            onClick = { showAdd = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("Добавить участников", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    // Ссылку может скопировать любой участник и отправить человеку.
                    if (chat.link.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(chat.linkLabel))
                                scope.launch {
                                    snackbarHost.showSnackbar("Ссылка скопирована")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Text("Скопировать ссылку", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                item {
                    // Обои живут здесь, потому что отдельной кнопки в шапке чата больше нет.
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showWallpaper = true },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wallpaper,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Обои чата",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }

                if (chat.link.isNotBlank()) {
                    item {
                        // Одна постоянная ссылка группы. Менять вручную нельзя,
                        // но владелец или администратор может её пересоздать.
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 12.dp),
                                    ) {
                                        Text(
                                            text = "Ссылка-приглашение",
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            text = chat.linkLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(chat.linkLabel))
                                            scope.launch {
                                                snackbarHost.showSnackbar("Ссылка скопирована")
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Копировать ссылку",
                                        )
                                    }
                                }
                                if (canManage) {
                                    TextButton(
                                        onClick = { showRegenerateLink = true },
                                        modifier = Modifier.align(Alignment.End),
                                    ) {
                                        Text("Пересоздать ссылку")
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Участники",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }

                items(members, key = { it.uid }) { member ->
                    val isMe = member.uid == viewModel.myUid
                    val role = when {
                        member.uid == chat.ownerId -> "Владелец"
                        member.uid in chat.admins -> "Администратор"
                        else -> ""
                    }
                    MemberRow(
                        member = member,
                        role = role,
                        canManage = canManage,
                        canChangeRole = isOwner && member.uid != chat.ownerId,
                        canRemove = canManage &&
                            member.uid != chat.ownerId &&
                            !isMe,
                        onRemove = { memberToRemove = member },
                        onMakeAdmin = { viewModel.setAdmin(member, true) },
                        onRemoveAdmin = { viewModel.setAdmin(member, false) },
                        onTransfer = { memberToTransfer = member },
                    )
                }

                item {
                    Spacer(Modifier.height(20.dp))
                    if (isOwner) {
                        OutlinedButton(
                            onClick = { showDelete = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = "Удалить группу",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showLeave = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = null,
                            )
                            Text("Выйти из группы", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    if (showEdit && chat != null) {
        EditGroupDialog(
            chat = chat,
            onDismiss = { showEdit = false },
            onSave = { title, photoUri ->
                viewModel.updateGroup(title, photoUri) { showEdit = false }
            },
        )
    }

    if (showAdd && chat != null) {
        AddMembersDialog(
            users = state.users.filter { it.uid !in chat.members },
            onDismiss = { showAdd = false },
            onAdd = { users ->
                viewModel.addMembers(users) { showAdd = false }
            },
        )
    }

    if (showWallpaper) {
        Dialog(
            onDismissRequest = { showWallpaper = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                WallpaperScreen(
                    current = state.wallpaper,
                    onSelect = { spec ->
                        viewModel.setWallpaper(spec)
                        showWallpaper = false
                    },
                    onPickPhoto = { pickWallpaper() },
                    onBack = { showWallpaper = false },
                )
            }
        }
    }

    if (showLeave) {
        ConfirmDialog(
            title = "Выйти из группы?",
            text = "Вы больше не будете получать сообщения этой группы.",
            confirmText = "Выйти",
            onDismiss = { showLeave = false },
            onConfirm = {
                showLeave = false
                viewModel.leaveGroup(onLeft)
            },
        )
    }

    if (showDelete) {
        ConfirmDialog(
            title = "Удалить группу?",
            text = "Группа и вся её история удалятся у всех участников. Это нельзя отменить.",
            confirmText = "Удалить",
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                viewModel.deleteGroup(onLeft)
            },
        )
    }

    if (showRegenerateLink) {
        ConfirmDialog(
            title = "Пересоздать ссылку?",
            text = "Старая ссылка и отправленные ранее приглашения перестанут работать. " +
                "Новую ссылку можно будет скопировать здесь же.",
            confirmText = "Пересоздать",
            onDismiss = { showRegenerateLink = false },
            onConfirm = {
                showRegenerateLink = false
                viewModel.regenerateLink {
                    scope.launch {
                        snackbarHost.showSnackbar("Ссылка пересоздана")
                    }
                }
            },
        )
    }

    memberToRemove?.let { member ->
        ConfirmDialog(
            title = "Удалить участника?",
            text = "${member.name} больше не будет видеть сообщения группы.",
            confirmText = "Удалить",
            onDismiss = { memberToRemove = null },
            onConfirm = {
                viewModel.removeMember(member)
                memberToRemove = null
            },
        )
    }

    memberToTransfer?.let { member ->
        ConfirmDialog(
            title = "Передать права?",
            text = "${member.name} станет владельцем группы, а вы — администратором.",
            confirmText = "Передать",
            onDismiss = { memberToTransfer = null },
            onConfirm = {
                viewModel.transferOwnership(member)
                memberToTransfer = null
            },
        )
    }
}

/** Строка участника с меню действий. */
@Composable
private fun MemberRow(
    member: User,
    role: String,
    canManage: Boolean,
    canChangeRole: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit,
    onMakeAdmin: () -> Unit,
    onRemoveAdmin: () -> Unit,
    onTransfer: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name = member.name, photoUrl = member.photoUrl, size = 42.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (role.isNotBlank()) {
                    Text(
                        text = role,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (canManage && (canChangeRole || canRemove)) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Действия")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        if (canChangeRole) {
                            if (role == "Администратор") {
                                DropdownMenuItem(
                                    text = { Text("Снять администратора") },
                                    onClick = {
                                        menuOpen = false
                                        onRemoveAdmin()
                                    },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Сделать администратором") },
                                    onClick = {
                                        menuOpen = false
                                        onMakeAdmin()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Передать права владельца") },
                                onClick = {
                                    menuOpen = false
                                    onTransfer()
                                },
                            )
                        }
                        if (canRemove) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Удалить из группы",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    onRemove()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Окно изменения названия и фото группы. */
@Composable
private fun EditGroupDialog(
    chat: Chat,
    onDismiss: () -> Unit,
    onSave: (String, Uri?) -> Unit,
) {
    var title by remember(chat.title) { mutableStateOf(chat.title) }
    var photo by remember { mutableStateOf<Uri?>(null) }
    val pickPhoto = rememberPhotoPicker { photo = it }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Изменить группу",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = pickPhoto),
                ) {
                    if (photo != null) {
                        AsyncImage(
                            model = photo,
                            contentDescription = "Новое фото группы",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(20.dp)),
                        )
                    } else {
                        Avatar(name = chat.displayTitle, photoUrl = chat.photoUrl, size = 64.dp)
                    }
                    Text(
                        text = "Нажмите, чтобы выбрать фото",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = { Text("Название") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Button(
                        onClick = { onSave(title, photo) },
                        enabled = title.trim().isNotEmpty(),
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}

/** Окно добавления участников: поиск по имени, нику или email. */
@Composable
private fun AddMembersDialog(
    users: List<User>,
    onDismiss: () -> Unit,
    onAdd: (List<User>) -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    var query by remember { mutableStateOf("") }

    // Ищем по тем же полям, что и при создании группы.
    val text = normalizeSearch(query)
    val found = if (text.isEmpty()) {
        emptyList()
    } else {
        users.filter { user ->
            normalizeSearch(user.name).contains(text) ||
                normalizeSearch(user.email).contains(text) ||
                normalizeSearch(user.username).contains(text)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Добавить участников",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))

                if (users.isEmpty()) {
                    Text(
                        text = "Все зарегистрированные пользователи уже в группе.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Имя, ник или email") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))

                    when {
                        text.isEmpty() -> Text(
                            text = "Введите имя, ник или email, чтобы найти человека.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        found.isEmpty() -> Text(
                            text = "Никого не нашли.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        else -> LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                        ) {
                            items(found, key = { it.uid }) { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selected = if (user.uid in selected) {
                                                selected - user.uid
                                            } else {
                                                selected + user.uid
                                            }
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Avatar(name = user.name, photoUrl = user.photoUrl, size = 38.dp)
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 10.dp),
                                    ) {
                                        Text(
                                            text = user.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = if (user.username.isBlank()) {
                                                user.email
                                            } else {
                                                user.usernameLabel
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Checkbox(
                                        checked = user.uid in selected,
                                        onCheckedChange = { checked ->
                                            selected = if (checked) {
                                                selected + user.uid
                                            } else {
                                                selected - user.uid
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Button(
                        onClick = { onAdd(users.filter { it.uid in selected }) },
                        enabled = selected.isNotEmpty(),
                    ) {
                        Text("Добавить")
                    }
                }
            }
        }
    }
}

/** Простое окно подтверждения опасного действия. */
@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
