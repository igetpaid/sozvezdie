package com.sozvezdie.messenger.core.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.sozvezdie.messenger.core.data.local.MessageDao
import com.sozvezdie.messenger.core.data.local.MessageEntity
import com.sozvezdie.messenger.core.data.local.toFoundMessage
import com.sozvezdie.messenger.core.data.local.toModel
import com.sozvezdie.messenger.core.model.FoundMessage
import com.sozvezdie.messenger.core.model.Message
import com.sozvezdie.messenger.core.model.MessageType
import com.sozvezdie.messenger.core.model.User
import com.sozvezdie.messenger.core.model.normalizeSearch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Сообщения конкретного чата.
 *
 * Новые сообщения приходят из Firebase и сразу сохраняются в Room.
 * Наружу отдаётся поток Room, поэтому переписка не пропадает
 * при перезапуске приложения и открывается без интернета.
 *
 * Реакции лежат внутри чата, в узле chats/{chatId}/reactions/{messageId}/{uid},
 * чтобы одно и то же сообщение могли отметить несколько человек.
 */
class MessageRepository(
    private val database: FirebaseDatabase,
    private val messageDao: MessageDao,
) {

    fun observeMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        var cached: List<Message> = emptyList()
        var reactions: Map<String, Map<String, String>> = emptyMap()

        fun emit() {
            trySend(
                cached.map { message ->
                    message.copy(reactions = reactions[message.id].orEmpty())
                },
            )
        }

        // Берём только последние 300 сообщений: всю историю чата в память не тянем.
        val messagesRef = database.reference.child("messages").child(chatId)
            .orderByChild("createdAt")
            .limitToLast(300)

        val messagesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children.mapNotNull { it.toEntity(chatId) }
                if (messages.isEmpty()) return
                launch { messageDao.upsert(messages) }
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        messagesRef.addValueEventListener(messagesListener)

        val reactionsRef = database.reference.child("chats").child(chatId).child("reactions")
        val reactionsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                reactions = snapshot.children.associate { message ->
                    val people = message.children.associate { item ->
                        item.key.orEmpty() to item.getValue(String::class.java).orEmpty()
                    }.filterValues { it.isNotBlank() }
                    message.key.orEmpty() to people
                }
                emit()
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        reactionsRef.addValueEventListener(reactionsListener)

        val roomJob = launch {
            messageDao.observeMessages(chatId).collect { entities ->
                cached = entities.map { it.toModel() }
                emit()
            }
        }

        awaitClose {
            messagesRef.removeEventListener(messagesListener)
            reactionsRef.removeEventListener(reactionsListener)
            roomJob.cancel()
        }
    }

    /** Поиск по сохранённой истории сообщений. */
    fun searchMessages(query: String): Flow<List<FoundMessage>> =
        messageDao.searchMessages(normalizeSearch(query)).map { rows ->
            rows.map { it.toFoundMessage() }
        }

    suspend fun sendText(
        chatId: String,
        sender: User,
        text: String,
        reply: Message? = null,
    ): Result<Unit> {
        val body = text.trim()
        if (body.isEmpty()) return Result.failure(Exception("Сообщение пустое"))
        return send(
            chatId = chatId,
            sender = sender,
            type = MessageType.TEXT,
            text = body,
            reply = reply,
        )
    }

    suspend fun sendImage(
        chatId: String,
        sender: User,
        mediaUrl: String,
        reply: Message? = null,
    ): Result<Unit> = send(
        chatId = chatId,
        sender = sender,
        type = MessageType.IMAGE,
        mediaUrl = mediaUrl,
        reply = reply,
    )

    suspend fun sendFile(
        chatId: String,
        sender: User,
        mediaUrl: String,
        fileName: String,
        fileSize: Long,
        reply: Message? = null,
    ): Result<Unit> = send(
        chatId = chatId,
        sender = sender,
        type = MessageType.FILE,
        mediaUrl = mediaUrl,
        fileName = fileName,
        fileSize = fileSize,
        reply = reply,
    )

    suspend fun sendVoice(
        chatId: String,
        sender: User,
        mediaUrl: String,
        durationMs: Long,
        waveform: String,
        reply: Message? = null,
    ): Result<Unit> = send(
        chatId = chatId,
        sender = sender,
        type = MessageType.VOICE,
        mediaUrl = mediaUrl,
        durationMs = durationMs,
        waveform = waveform,
        reply = reply,
    )

    /**
     * Ставит или убирает реакцию.
     *
     * Если человек нажал тот же эмодзи второй раз, реакция снимается.
     */
    suspend fun toggleReaction(
        chatId: String,
        messageId: String,
        uid: String,
        emoji: String,
    ): Result<Unit> = runCatching {
        val ref = database.reference
            .child("chats").child(chatId).child("reactions").child(messageId).child(uid)
        val current = ref.get().await().getValue(String::class.java)
        if (current == emoji) {
            ref.removeValue().await()
        } else {
            ref.setValue(emoji).await()
        }
        Unit
    }.mapError("Не удалось поставить реакцию")

    private suspend fun send(
        chatId: String,
        sender: User,
        type: MessageType,
        text: String = "",
        mediaUrl: String = "",
        fileName: String = "",
        fileSize: Long = 0L,
        durationMs: Long = 0L,
        waveform: String = "",
        reply: Message? = null,
    ): Result<Unit> = runCatching {
        // push() заранее создаёт уникальный ключ сообщения. Благодаря этому
        // два человека могут отправить сообщение одновременно, и они не перезапишут друг друга.
        val newMessageRef = database.reference.child("messages").child(chatId).push()
        val messageId = newMessageRef.key ?: error("Не удалось создать сообщение")

        val message = mapOf(
            "id" to messageId,
            "senderId" to sender.uid,
            "senderName" to sender.name,
            "senderPhotoUrl" to sender.photoUrl,
            "type" to MessageType.toKey(type),
            "text" to text,
            "mediaUrl" to mediaUrl,
            "fileName" to fileName,
            "fileSize" to fileSize,
            "durationMs" to durationMs,
            "waveform" to waveform,
            "replyToId" to (reply?.id ?: ""),
            "replyToText" to (reply?.replyPreview() ?: ""),
            "replyToSender" to (reply?.senderName ?: ""),
            "createdAt" to ServerValue.TIMESTAMP,
        )

        val previewText = when (type) {
            MessageType.IMAGE -> "Фотография"
            MessageType.FILE -> fileName.ifBlank { "Файл" }
            MessageType.VOICE -> "Голосовое сообщение"
            MessageType.SYSTEM -> text
            MessageType.TEXT -> text
        }
        val preview = mapOf(
            "text" to previewText,
            "senderId" to sender.uid,
            "senderName" to sender.name,
            "type" to MessageType.toKey(type),
            "createdAt" to ServerValue.TIMESTAMP,
        )

        // Сообщение и превью в списке чатов обновляем одной операцией.
        database.reference.updateChildren(
            mapOf(
                "messages/$chatId/$messageId" to message,
                "chats/$chatId/lastMessage" to preview,
                "chats/$chatId/updatedAt" to ServerValue.TIMESTAMP,
            ),
        ).await()
        Unit
    }.mapError("Не удалось отправить сообщение")

    private fun DataSnapshot.toEntity(chatId: String): MessageEntity? {
        val id = child("id").getValue(String::class.java) ?: key ?: return null
        val type = MessageType.fromKey(child("type").getValue(String::class.java))
        val text = child("text").getValue(String::class.java).orEmpty()
        val fileName = child("fileName").getValue(String::class.java).orEmpty()
        val senderName = child("senderName").getValue(String::class.java).orEmpty()

        return MessageEntity(
            id = id,
            chatId = chatId,
            senderId = child("senderId").getValue(String::class.java).orEmpty(),
            senderName = senderName,
            senderPhotoUrl = child("senderPhotoUrl").getValue(String::class.java).orEmpty(),
            type = MessageType.toKey(type),
            text = text,
            mediaUrl = child("mediaUrl").getValue(String::class.java).orEmpty(),
            fileName = fileName,
            fileSize = child("fileSize").getValue(Long::class.java) ?: 0L,
            durationMs = child("durationMs").getValue(Long::class.java) ?: 0L,
            waveform = child("waveform").getValue(String::class.java).orEmpty(),
            replyToId = child("replyToId").getValue(String::class.java).orEmpty(),
            replyToText = child("replyToText").getValue(String::class.java).orEmpty(),
            replyToSender = child("replyToSender").getValue(String::class.java).orEmpty(),
            searchText = normalizeSearch("$text $fileName $senderName"),
            status = "SENT",
            createdAt = child("createdAt").getValue(Long::class.java) ?: 0L,
        )
    }

    /** Удаляет сообщение (только своё, по правилам Firebase). */
    suspend fun deleteMessage(chatId: String, messageId: String): Result<Unit> = runCatching {
        // Firebase проверяет автора, поэтому чужое сообщение удалить не получится.
        // Room чистим только после успешного ответа сервера.
        database.reference.child("messages").child(chatId).child(messageId).removeValue().await()
        messageDao.deleteMessage(chatId, messageId)
    }

    private fun <T> Result<T>.mapError(fallback: String): Result<T> {
        val error = exceptionOrNull() ?: return this
        return Result.failure(Exception(error.message ?: fallback))
    }
}
