package com.sozvezdie.messenger.feature.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sozvezdie.messenger.core.model.Chat
import com.sozvezdie.messenger.core.model.ChatType
import com.sozvezdie.messenger.core.model.Message
import com.sozvezdie.messenger.core.model.MessageType
import com.sozvezdie.messenger.core.ui.components.Avatar
import com.sozvezdie.messenger.core.ui.components.LoadingScreen
import com.sozvezdie.messenger.core.ui.components.WallpaperBackground
import com.sozvezdie.messenger.core.ui.theme.BubbleMine
import com.sozvezdie.messenger.core.ui.util.formatDayLabel
import com.sozvezdie.messenger.core.ui.util.formatDuration
import com.sozvezdie.messenger.core.ui.util.formatLastSeen
import com.sozvezdie.messenger.core.ui.util.formatMessageTime
import com.sozvezdie.messenger.feature.media.FileMessageCard
import com.sozvezdie.messenger.feature.media.ImagePreviewDialog
import com.sozvezdie.messenger.feature.media.MessageImage
import com.sozvezdie.messenger.feature.media.openCachedFile
import com.sozvezdie.messenger.feature.media.rememberFilePicker
import com.sozvezdie.messenger.feature.media.rememberPhotoPicker
import com.sozvezdie.messenger.feature.motion.AnimatedMessage
import com.sozvezdie.messenger.feature.motion.TypingDots
import kotlinx.coroutines.delay

// Эмодзи, которые можно поставить на сообщение.
private val reactionEmojis = listOf("❤️", "👍", "😂", "😮", "😢", "🙏")

/** Сообщение вместе с подписью дня, если день новый. */
private data class ChatEntry(
    val message: Message,
    val dayLabel: String?,
)

/**
 * Экран переписки: сообщения, статус собеседника и поле ввода.
 *
 * Экран только показывает данные из ChatViewModel и вызывает его методы.
 * Запись голосового живёт здесь, потому что это состояние микрофона,
 * а не данные чата.
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenGroupInfo: () -> Unit,
    onViewProfile: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Когда возвращаемся из настроек группы, подхватываем новые обои.
    LaunchedEffect(Unit) {
        viewModel.reloadLocalSettings()
    }

    ChatContent(
        state = state,
        myUid = viewModel.currentUserId,
        onBack = onBack,
        onOpenGroupInfo = onOpenGroupInfo,
        onViewProfile = onViewProfile,
        onSendText = viewModel::sendText,
        onTextChanged = viewModel::onTextChanged,
        onSendImage = viewModel::sendImage,
        onSendFile = viewModel::sendFile,
        onSendVoice = viewModel::sendVoice,
        onFileClick = viewModel::openFile,
        onFileOpened = viewModel::onFileOpened,
        onSetReply = viewModel::setReply,
        onClearReply = viewModel::clearReply,
        onReaction = viewModel::toggleReaction,
        onDelete = viewModel::deleteMessage,
        onSetWallpaper = viewModel::setWallpaper,
        onCustomWallpaper = viewModel::saveCustomWallpaper,
        onError = viewModel::reportError,
        onClearError = viewModel::clearError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatContent(
    state: ChatUiState,
    myUid: String,
    onBack: () -> Unit,
    onOpenGroupInfo: () -> Unit,
    onViewProfile: (String) -> Unit,
    onSendText: (String) -> Unit,
    onTextChanged: (String) -> Unit,
    onSendImage: (Uri) -> Unit,
    onSendFile: (Uri) -> Unit,
    onSendVoice: (java.io.File, Long) -> Unit,
    onFileClick: (String, String) -> Unit,
    onFileOpened: () -> Unit,
    onSetReply: (Message) -> Unit,
    onClearReply: () -> Unit,
    onReaction: (Message, String) -> Unit,
    onDelete: (Message) -> Unit,
    onSetWallpaper: (String) -> Unit,
    onCustomWallpaper: (Uri) -> Unit,
    onError: (String) -> Unit,
    onClearError: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboard = LocalClipboardManager.current
    val chat = state.chat
    val isGroup = chat?.type == ChatType.GROUP

    var text by remember(chat?.id) { mutableStateOf(state.draft) }
    var attachMenuOpen by remember { mutableStateOf(false) }
    var chatMenuOpen by remember { mutableStateOf(false) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<Message?>(null) }
    var messageToDelete by remember { mutableStateOf<Message?>(null) }
    var showWallpaper by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }

    val recorder = remember { VoiceRecorder(context) }
    val snackbarHost = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val pickPhoto = rememberPhotoPicker { uri -> onSendImage(uri) }
    val pickFile = rememberFilePicker { uri -> onSendFile(uri) }
    val pickWallpaper = rememberPhotoPicker { uri ->
        onCustomWallpaper(uri)
        showWallpaper = false
    }

    val startRecording = {
        recorder.start()
            .onSuccess {
                recording = true
                elapsed = 0L
            }
            .onFailure { onError("Не удалось начать запись. Проверьте микрофон") }
        Unit
    }

    val stopRecording = {
        recording = false
        val result = recorder.stop()
        if (result == null) {
            onError("Слишком короткое голосовое")
        } else {
            onSendVoice(result.file, result.durationMs)
        }
    }

    val cancelRecording = {
        recording = false
        elapsed = 0L
        recorder.cancel()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startRecording() else onError("Разрешите доступ к микрофону")
    }

    val onMicClick = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startRecording() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Таймер записи. На пяти минутах останавливаемся сами.
    LaunchedEffect(recording) {
        while (recording) {
            elapsed = recorder.elapsedMs()
            if (elapsed >= VoiceRecorder.MAX_DURATION_MS) {
                stopRecording()
            }
            delay(200)
        }
    }

    // Если приложение свернули, запись не должна продолжаться.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && recorder.isRecording) {
                recorder.cancel()
                recording = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            recorder.cancel()
            VoicePlayback.stop()
        }
    }

    val entries = remember(state.messages) {
        val reversed = state.messages.reversed()
        reversed.mapIndexed { index, message ->
            val day = formatDayLabel(message.createdAt)
            // При reverseLayout day divider должен стоять над сообщениями дня,
            // поэтому показываем его для последнего сообщения дня в обратном порядке.
            val nextDay = reversed.getOrNull(index + 1)?.let { formatDayLabel(it.createdAt) }
            val showDay = day != nextDay
            ChatEntry(message, if (showDay) day else null)
        }
    }

    // При изменении списка сообщений скроллим вниз.
    // scrollToItem (без анимации) надёжнее при первом открытии чата,
    // когда сообщения приходят асинхронно.
    // Дополнительно отслеживаем изменение размера LazyColumn (например, при открытии клавиатуры).
    // При появлении новых сообщений скроллим вниз (index 0 при reverseLayout = низ экрана).
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { message ->
            snackbarHost.showSnackbar(message)
            onClearError()
        }
    }

    LaunchedEffect(state.fileToOpen) {
        state.fileToOpen?.let { file ->
            openCachedFile(context, file)
            onFileOpened()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                title = {
                    // В группе нажимается вся шапка целиком, как в Telegram.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isGroup) onOpenGroupInfo() else {
                                    val uid = state.chat?.otherUserId
                                    if (!uid.isNullOrBlank()) onViewProfile(uid)
                                }
                            },
                    ) {
                        Avatar(
                            name = chat?.displayTitle ?: "Чат",
                            photoUrl = if (isGroup) {
                                chat?.photoUrl.orEmpty()
                            } else {
                                chat?.otherUserPhotoUrl.orEmpty()
                            },
                            size = 40.dp,
                        )
                        Column(modifier = Modifier.padding(start = 10.dp)) {
                            Text(
                                text = chat?.displayTitle ?: "Чат",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            ChatSubtitle(state)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
                actions = {
                    // В личном чате обои выбираются в меню из трёх точек.
                    // В группе они живут в информации о группе.
                    if (!isGroup) {
                        Box {
                            IconButton(onClick = { chatMenuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Меню чата")
                            }
                            DropdownMenu(
                                expanded = chatMenuOpen,
                                onDismissRequest = { chatMenuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Обои чата") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Wallpaper, contentDescription = null)
                                    },
                                    onClick = {
                                        chatMenuOpen = false
                                        showWallpaper = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            InputBar(
                text = text,
                reply = state.replyTo,
                uploading = state.uploading,
                recording = recording,
                elapsed = elapsed,
                attachMenuOpen = attachMenuOpen,
                onTextChange = {
                    text = it
                    onTextChanged(it)
                },
                onAttachMenuChange = { attachMenuOpen = it },
                onPickPhoto = pickPhoto,
                onPickFile = pickFile,
                onMicClick = onMicClick,
                onStopRecording = stopRecording,
                onCancelRecording = cancelRecording,
                onClearReply = onClearReply,
                onSend = {
                    if (text.isNotBlank()) {
                        onSendText(text)
                        text = ""
                        onTextChanged("")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            WallpaperBackground(spec = state.wallpaper)

            when {
                state.loading && state.messages.isEmpty() -> LoadingScreen()

                state.messages.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Сообщений пока нет",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Напишите первое сообщение или отправьте фотографию.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    reverseLayout = true,
                ) {
                    itemsIndexed(entries, key = { _, entry -> entry.message.id }) { index, entry ->
                        // Всё содержимое item держим в одном Column.
                        // При reverseLayout Compose переставляет местами несколько
                        // корневых элементов внутри item, и тогда дата оказывается под сообщением.
                        Column {
                            entry.dayLabel?.let { label ->
                                DayDivider(label)
                            }
                            val previous = entries.getOrNull(index - 1)?.message
                            AnimatedMessage {
                                MessageRow(
                                    message = entry.message,
                                    previous = previous,
                                    chat = chat,
                                    isMine = entry.message.senderId == myUid,
                                    myUid = myUid,
                                    onLongPress = { actionMessage = entry.message },
                                    onImageClick = { previewUrl = entry.message.mediaUrl },
                                    onFileClick = onFileClick,
                                    onReactionClick = { emoji ->
                                        onReaction(entry.message, emoji)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    previewUrl?.let { url ->
        ImagePreviewDialog(url = url, onDismiss = { previewUrl = null })
    }

    actionMessage?.let { message ->
        MessageActionsDialog(
            message = message,
            isMine = message.senderId == myUid,
            onDismiss = { actionMessage = null },
            onReaction = { emoji ->
                onReaction(message, emoji)
                actionMessage = null
            },
            onReply = {
                onSetReply(message)
                actionMessage = null
            },
            onCopy = {
                clipboard.setText(AnnotatedString(message.text))
                actionMessage = null
            },
            onDelete = {
                messageToDelete = message
                actionMessage = null
            },
        )
    }

    // Перед удалением спрашиваем подтверждение: действие нельзя отменить.
    messageToDelete?.let { message ->
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text("Удалить сообщение?") },
            text = { Text("Сообщение пропадёт у всех участников чата.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(message)
                        messageToDelete = null
                    },
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) {
                    Text("Отмена")
                }
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
                        onSetWallpaper(spec)
                        showWallpaper = false
                    },
                    onPickPhoto = { pickWallpaper() },
                    onBack = { showWallpaper = false },
                )
            }
        }
    }
}

/** Вторая строка заголовка: «печатает», «в сети» или число участников. */
@Composable
private fun ChatSubtitle(state: ChatUiState) {
    val typing = state.typingNames
    val chat = state.chat
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant

    when {
        typing.isNotEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (typing.size == 1) "${typing.first()} печатает" else "печатают…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(6.dp))
            TypingDots(color = MaterialTheme.colorScheme.primary)
        }

        chat?.type == ChatType.GROUP -> Text(
            text = "${chat.memberCount} участников",
            style = MaterialTheme.typography.labelSmall,
            color = subtitleColor,
        )

        state.presence.online -> Text(
            text = "в сети",
            style = MaterialTheme.typography.labelSmall,
            color = subtitleColor,
        )

        else -> Text(
            text = formatLastSeen(state.presence.lastSeen),
            style = MaterialTheme.typography.labelSmall,
            color = subtitleColor,
            maxLines = 1,
        )
    }
}

/** Разделитель между днями переписки. */
@Composable
private fun DayDivider(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Одно сообщение.
 *
 * Свои сообщения справа и цветные, чужие слева и светлые.
 * Долгое нажатие открывает реакции и ответ.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    message: Message,
    previous: Message?,
    chat: Chat?,
    isMine: Boolean,
    myUid: String,
    onLongPress: () -> Unit,
    onImageClick: () -> Unit,
    onFileClick: (String, String) -> Unit,
    onReactionClick: (String) -> Unit,
) {
    if (message.type == MessageType.SYSTEM) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        return
    }

    val bubbleColor = if (isMine) BubbleMine else MaterialTheme.colorScheme.surface
    val contentColor = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
    val shape = if (isMine) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }
    val isGroup = chat?.type == ChatType.GROUP
    val showSender = isGroup && !isMine && previous?.senderId != message.senderId

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isMine && isGroup) {
            Avatar(
                name = message.senderName.ifBlank { "?" },
                photoUrl = message.senderPhotoUrl,
                size = 30.dp,
                modifier = Modifier.padding(end = 6.dp),
            )
        }

        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            if (showSender) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
                )
            }

            Surface(
                color = bubbleColor,
                shape = shape,
                modifier = Modifier.combinedClickable(
                    onLongClick = onLongPress,
                    onClick = onLongPress,
                ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) {
                    if (message.replyToId.isNotBlank()) {
                        ReplyPreview(message = message, isMine = isMine)
                        Spacer(Modifier.height(4.dp))
                    }

                    when (message.type) {
                        MessageType.IMAGE -> MessageImage(
                            url = message.mediaUrl,
                            onClick = onImageClick,
                        )

                        MessageType.FILE -> FileMessageCard(
                            fileName = message.fileName.ifBlank { "Файл" },
                            isMine = isMine,
                            onClick = {
                                onFileClick(message.mediaUrl, message.fileName.ifBlank { "Файл" })
                            },
                        )

                        MessageType.VOICE -> VoiceMessage(
                            message = message,
                            isMine = isMine,
                        )

                        MessageType.TEXT, MessageType.SYSTEM -> {
                            val emojiSize = emojiFontSize(message.text)
                            Text(
                                text = message.text,
                                color = contentColor,
                                style = if (emojiSize == null) {
                                    MaterialTheme.typography.bodyLarge
                                } else {
                                    MaterialTheme.typography.bodyLarge.copy(fontSize = emojiSize)
                                },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatMessageTime(message.createdAt),
                            color = if (isMine) {
                                Color.White.copy(alpha = 0.72f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (isMine) {
                            val readAt = chat?.allOthersReadAt(myUid) ?: 0L
                            val read = readAt >= message.createdAt && message.createdAt > 0L
                            Icon(
                                imageVector = if (read) {
                                    Icons.Default.DoneAll
                                } else {
                                    Icons.Default.Check
                                },
                                contentDescription = if (read) "Прочитано" else "Отправлено",
                                tint = if (read) {
                                    Color(0xFF9BE7FF)
                                } else {
                                    Color.White.copy(alpha = 0.72f)
                                },
                                modifier = Modifier
                                    .padding(start = 3.dp)
                                    .size(14.dp),
                            )
                        }
                    }
                }
            }

            val counts = message.reactionCounts()
            if (counts.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 3.dp),
                ) {
                    counts.entries.sortedByDescending { it.value }.forEach { (emoji, count) ->
                        val mine = message.reactions[myUid] == emoji
                        Surface(
                            color = if (mine) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.clickable { onReactionClick(emoji) },
                        ) {
                            Text(
                                text = "$emoji $count",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Короткий блок «в ответ на…» внутри пузыря. */
@Composable
private fun ReplyPreview(message: Message, isMine: Boolean) {
    val accent = if (isMine) Color.White else MaterialTheme.colorScheme.primary
    val background = if (isMine) {
        Color.White.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        Text(
            text = message.replyToSender,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = message.replyToText,
            style = MaterialTheme.typography.bodySmall,
            color = if (isMine) Color.White.copy(alpha = 0.85f) else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Голосовое: кнопка воспроизведения, волна и длительность. */
@Composable
private fun VoiceMessage(message: Message, isMine: Boolean) {
    val playing by VoicePlayback.playingUrl.collectAsStateWithLifecycle()
    val loading by VoicePlayback.loadingUrl.collectAsStateWithLifecycle()
    val contentColor = if (isMine) Color.White else MaterialTheme.colorScheme.primary
    val active = playing == message.mediaUrl

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { VoicePlayback.toggle(message.mediaUrl) },
            modifier = Modifier.size(38.dp),
        ) {
            when {
                loading == message.mediaUrl -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )

                else -> Icon(
                    imageVector = if (active) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (active) "Пауза" else "Слушать",
                    tint = contentColor,
                )
            }
        }

        Waveform(message = message, isMine = isMine)

        Text(
            text = formatDuration(message.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = if (isMine) Color.White.copy(alpha = 0.8f) else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Простая волна: столбики разной высоты из строки сообщения. */
@Composable
private fun Waveform(message: Message, isMine: Boolean) {
    val bars = remember(message.waveform, message.id) {
        val parsed = message.waveform.split(',').mapNotNull { it.trim().toIntOrNull() }
        if (parsed.isNotEmpty()) {
            parsed
        } else {
            List(28) { index ->
                ((message.id.hashCode().ushr(index % 24)) and 7) + 2
            }
        }
    }
    val color = if (isMine) Color.White.copy(alpha = 0.85f) else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 2.dp),
    ) {
        bars.take(32).forEach { value ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height((3 + value.coerceIn(1, 9) * 1.6f).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

/** Окно действий с сообщением: реакции, ответ и копирование. */
@Composable
private fun MessageActionsDialog(
    message: Message,
    isMine: Boolean,
    onDismiss: () -> Unit,
    onReaction: (String) -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = message.text.ifBlank { message.replyPreview() },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    reactionEmojis.forEach { emoji ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(42.dp)
                                .clickable { onReaction(emoji) },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = emoji, fontSize = 20.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                TextButton(onClick = onReply, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null)
                    Text("Ответить", modifier = Modifier.padding(start = 10.dp))
                }
                if (message.type == MessageType.TEXT && message.text.isNotBlank()) {
                    TextButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Text("Копировать текст", modifier = Modifier.padding(start = 10.dp))
                    }
                }
                if (isMine) {
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text("Удалить", modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
        }
    }
}

/** Поле ввода, вложения и панель записи голосового. */
@Composable
private fun InputBar(
    text: String,
    reply: Message?,
    uploading: Boolean,
    recording: Boolean,
    elapsed: Long,
    attachMenuOpen: Boolean,
    onTextChange: (String) -> Unit,
    onAttachMenuChange: (Boolean) -> Unit,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onMicClick: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onClearReply: () -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        // Берём больший из двух отступов: клавиатура или системная навигация.
        // Так панель не прячется за кнопками навигации и не прыгает с клавиатурой.
        Column(
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.ime.union(WindowInsets.navigationBars),
            ),
        ) {
            if (reply != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    ) {
                        Text(
                            text = reply.senderName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = reply.replyPreview(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onClearReply, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Отменить ответ")
                    }
                }
            }

            if (uploading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "Загрузка файла…",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            if (recording) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFFEF4444), CircleShape),
                    )
                    Text(
                        text = "Запись ${formatDuration(elapsed)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCancelRecording) {
                        Text("Отмена")
                    }
                    FilledIconButton(onClick = onStopRecording) {
                        Icon(Icons.Default.Stop, contentDescription = "Отправить голосовое")
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        // Кнопка «прикрепить» такая же заливная и крупная, как остальные.
                        FilledIconButton(
                            onClick = { onAttachMenuChange(true) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Прикрепить",
                            )
                        }
                        DropdownMenu(
                            expanded = attachMenuOpen,
                            onDismissRequest = { onAttachMenuChange(false) },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Фотография") },
                                leadingIcon = {
                                    Icon(Icons.Default.Image, contentDescription = null)
                                },
                                onClick = {
                                    onAttachMenuChange(false)
                                    onPickPhoto()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Файл") },
                                leadingIcon = {
                                    Icon(Icons.Default.Description, contentDescription = null)
                                },
                                onClick = {
                                    onAttachMenuChange(false)
                                    onPickFile()
                                },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        placeholder = { Text("Сообщение") },
                        maxLines = 5,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                    )

                    if (text.isBlank()) {
                        FilledIconButton(
                            onClick = onMicClick,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Голосовое")
                        }
                    } else {
                        FilledIconButton(
                            onClick = onSend,
                            enabled = !uploading,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Отправить",
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Если сообщение состоит из одного-трёх эмодзи, показываем его крупно.
 * Возвращает null для обычного текста.
 */
private fun emojiFontSize(text: String): TextUnit? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null

    val codePoints = trimmed.codePoints().toArray()
    var emojiCount = 0
    for (point in codePoints) {
        val type = Character.getType(point)
        when {
            type == Character.OTHER_SYMBOL.toInt() -> emojiCount++
            Character.isWhitespace(point) -> Unit
            point == 0xFE0F || point == 0x200D -> Unit
            else -> return null
        }
    }

    return when {
        emojiCount == 1 -> 56.sp
        emojiCount in 2..3 -> 44.sp
        else -> null
    }
}
