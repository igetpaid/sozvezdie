package com.sozvezdie.messenger.core.data.local

import com.sozvezdie.messenger.core.model.Chat
import com.sozvezdie.messenger.core.model.ChatRole
import com.sozvezdie.messenger.core.model.ChatType
import com.sozvezdie.messenger.core.model.FoundMessage
import com.sozvezdie.messenger.core.model.Message
import com.sozvezdie.messenger.core.model.MessageStatus
import com.sozvezdie.messenger.core.model.MessageType

// Простые преобразования между таблицами Room и моделями приложения.
// Так экраны не знают про Entity, а база не знает про удобные классы.

fun ChatEntity.toModel(): Chat = Chat(
    id = id,
    type = ChatType.fromKey(type),
    title = title,
    link = link,
    photoUrl = photoUrl,
    ownerId = ownerId,
    otherUserId = otherUserId,
    otherUserName = otherUserName,
    otherUserPhotoUrl = otherUserPhotoUrl,
    lastMessageText = lastMessageText,
    lastMessageAt = lastMessageAt,
    lastMessageSenderId = lastMessageSenderId,
    lastMessageSenderName = lastMessageSenderName,
    lastMessageType = MessageType.fromKey(lastMessageType),
    myReadAt = myReadAt,
    otherReadAt = otherReadAt,
    memberCount = memberCount,
    myRole = runCatching { ChatRole.valueOf(myRole) }.getOrDefault(ChatRole.MEMBER),
    searchText = searchText,
)

fun MessageEntity.toModel(): Message = Message(
    id = id,
    chatId = chatId,
    senderId = senderId,
    senderName = senderName,
    senderPhotoUrl = senderPhotoUrl,
    type = MessageType.fromKey(type),
    text = text,
    mediaUrl = mediaUrl,
    fileName = fileName,
    fileSize = fileSize,
    durationMs = durationMs,
    waveform = waveform,
    replyToId = replyToId,
    replyToText = replyToText,
    replyToSender = replyToSender,
    createdAt = createdAt,
    status = runCatching { MessageStatus.valueOf(status) }.getOrDefault(MessageStatus.SENT),
)

fun MessageSearchRow.toFoundMessage(): FoundMessage {
    val title = if (chatType == "group") {
        chatTitle.ifBlank { "Группа" }
    } else {
        chatOtherName.ifBlank { "Чат" }
    }
    return FoundMessage(
        message = message.toModel(),
        chatId = message.chatId,
        chatTitle = title,
    )
}
