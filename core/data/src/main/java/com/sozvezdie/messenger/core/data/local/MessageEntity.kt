package com.sozvezdie.messenger.core.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Сообщение в локальной базе. История хранится даже без интернета. */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["chatId", "createdAt"]),
        Index(value = ["searchText"]),
    ],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val senderPhotoUrl: String,
    val type: String,
    val text: String,
    val mediaUrl: String,
    val fileName: String,
    val fileSize: Long,
    val durationMs: Long,
    val waveform: String,
    val replyToId: String,
    val replyToText: String,
    val replyToSender: String,
    val searchText: String,
    val status: String,
    val createdAt: Long,
)
