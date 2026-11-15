package com.sozvezdie.messenger.core.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Чат в локальной базе Room.
 *
 * Это не копия узла Firebase, а удобная плоская таблица для списка диалогов:
 * имя собеседника и последнее сообщение уже разложены по полям.
 */
@Entity(
    tableName = "chats",
    indices = [Index(value = ["lastMessageAt"])],
)
data class ChatEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    // Короткий код группы. У личных чатов поле пустое.
    val link: String = "",
    val photoUrl: String,
    val ownerId: String,
    val otherUserId: String,
    val otherUserName: String,
    val otherUserPhotoUrl: String,
    val lastMessageText: String,
    val lastMessageAt: Long,
    val lastMessageSenderId: String,
    val lastMessageSenderName: String,
    val lastMessageType: String,
    val myReadAt: Long,
    val otherReadAt: Long,
    val memberCount: Int,
    val myRole: String,
    val searchText: String,
)
