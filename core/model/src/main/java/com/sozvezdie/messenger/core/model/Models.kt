package com.sozvezdie.messenger.core.model

/**
 * Пользователь мессенджера.
 *
 * Онлайн и время последнего входа лежат отдельно в узле presence,
 * поэтому в обычном профиле эти поля могут быть пустыми.
 */
data class User(
    val uid: String,
    val email: String,
    val name: String,
    val username: String = "",
    val photoUrl: String = "",
    val about: String = "",
    val online: Boolean = false,
    val lastSeen: Long = 0L,
    val privacy: PrivacySettings = PrivacySettings(),
) {
    // Первая буква имени для аватара-кружка.
    val initial: String
        get() = name.trim().firstOrNull()?.uppercase() ?: "?"

    // Ник всегда показываем с собачкой, как в Telegram.
    val usernameLabel: String
        get() = if (username.isBlank()) "" else "@$username"
}

/** Состояние «в сети» для собеседника. */
data class Presence(
    val online: Boolean = false,
    val lastSeen: Long = 0L,
)

/** Кто может видеть поле профиля. */
enum class PrivacyLevel {
    ALL,
    CHATS,
    NOBODY;

    companion object {
        fun fromKey(key: String?): PrivacyLevel = when (key) {
            "chats" -> CHATS
            "nobody" -> NOBODY
            else -> ALL
        }

        fun toKey(level: PrivacyLevel): String = when (level) {
            ALL -> "all"
            CHATS -> "chats"
            NOBODY -> "nobody"
        }
    }
}

/** Настройки приватности профиля. */
data class PrivacySettings(
    val email: PrivacyLevel = PrivacyLevel.ALL,
    val about: PrivacyLevel = PrivacyLevel.ALL,
    val lastSeen: PrivacyLevel = PrivacyLevel.ALL,
)

/** Тип чата: личная переписка или группа. */
enum class ChatType {
    DIRECT,
    GROUP;

    companion object {
        fun fromKey(key: String?): ChatType = when (key) {
            "group" -> GROUP
            else -> DIRECT
        }

        fun toKey(type: ChatType): String = when (type) {
            DIRECT -> "direct"
            GROUP -> "group"
        }
    }
}

/** Роль участника группы. */
enum class ChatRole {
    OWNER,
    ADMIN,
    MEMBER,
}

/** Тип сообщения. В базе хранится короткой строкой. */
enum class MessageType {
    TEXT,
    IMAGE,
    FILE,
    VOICE,
    SYSTEM;

    companion object {
        fun fromKey(key: String?): MessageType = when (key) {
            "image" -> IMAGE
            "file" -> FILE
            "voice" -> VOICE
            "system" -> SYSTEM
            else -> TEXT
        }

        fun toKey(type: MessageType): String = when (type) {
            TEXT -> "text"
            IMAGE -> "image"
            FILE -> "file"
            VOICE -> "voice"
            SYSTEM -> "system"
        }
    }
}

/** Состояние отправки сообщения. */
enum class MessageStatus {
    SENDING,
    SENT,
    READ,
    FAILED,
}

/**
 * Чат в списке диалогов.
 *
 * Поля lastMessage* дублируют последнее сообщение: так список чатов
 * не должен читать всю переписку целиком.
 */
data class Chat(
    val id: String,
    val type: ChatType = ChatType.DIRECT,
    val title: String = "",
    // Короткий код группы без собачки. Например, groupk7x2.
    val link: String = "",
    val photoUrl: String = "",
    val ownerId: String = "",
    val otherUserId: String = "",
    val otherUserName: String = "",
    val otherUserPhotoUrl: String = "",
    val members: Set<String> = emptySet(),
    val memberNames: Map<String, String> = emptyMap(),
    val memberPhotos: Map<String, String> = emptyMap(),
    val admins: Set<String> = emptySet(),
    val readAt: Map<String, Long> = emptyMap(),
    val lastMessageText: String = "",
    val lastMessageAt: Long = 0L,
    val lastMessageSenderId: String = "",
    val lastMessageSenderName: String = "",
    val lastMessageType: MessageType = MessageType.TEXT,
    val myReadAt: Long = 0L,
    val otherReadAt: Long = 0L,
    val memberCount: Int = members.size,
    val myRole: ChatRole = ChatRole.MEMBER,
    val searchText: String = "",
) {
    // Название для шапки: у группы своё, у личного чата имя собеседника.
    val displayTitle: String
        get() = if (type == ChatType.GROUP) {
            title.ifBlank { "Группа" }
        } else {
            otherUserName.ifBlank { "Чат" }
        }

    // Ссылка группы в том виде, в котором её удобно показывать людям.
    val linkLabel: String
        get() = if (link.isBlank()) "" else "@$link"

    // Есть ли непрочитанное: последнее сообщение чужое и новее моей отметки.
    fun unreadFor(myUid: String): Boolean =
        lastMessageAt > myReadAt && lastMessageSenderId != myUid

    // В личном чате галочки загораются, когда прочитал собеседник.
    // В группе тогда, когда прочитали все остальные участники.
    fun isReadByOthers(myUid: String): Boolean {
        if (lastMessageAt <= 0L) return false
        val others = members.filter { it != myUid }
        if (others.isEmpty()) return false
        return others.all { (readAt[it] ?: 0L) >= lastMessageAt }
    }

    // Время, до которого прочитали все остальные. Нужно для галочек.
    fun allOthersReadAt(myUid: String): Long {
        val others = members.filter { it != myUid }
        if (others.isEmpty()) return 0L
        return others.minOfOrNull { readAt[it] ?: 0L } ?: 0L
    }
}

/**
 * Приглашение в группу.
 *
 * Хранится отдельно для каждого приглашённого, поэтому человек видит
 * приглашение в списке чатов, даже если приложение было закрыто.
 */
data class GroupInvite(
    val chatId: String,
    val title: String = "",
    val photoUrl: String = "",
    val link: String = "",
    val fromUid: String = "",
    val fromName: String = "",
    val createdAt: Long = 0L,
)

/**
 * Сообщение переписки.
 *
 * mediaUrl: прямая ссылка на файл в Supabase Storage.
 * Пустая строка означает, что вложения нет.
 */
data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val senderPhotoUrl: String = "",
    val type: MessageType = MessageType.TEXT,
    val text: String = "",
    val mediaUrl: String = "",
    val fileName: String = "",
    val fileSize: Long = 0L,
    val durationMs: Long = 0L,
    val waveform: String = "",
    val replyToId: String = "",
    val replyToText: String = "",
    val replyToSender: String = "",
    val createdAt: Long = 0L,
    val status: MessageStatus = MessageStatus.SENT,
    val reactions: Map<String, String> = emptyMap(),
) {
    val isText: Boolean get() = type == MessageType.TEXT
    val isMine: Boolean get() = senderId.isNotBlank()

    // Сколько раз поставили каждый эмодзи.
    fun reactionCounts(): Map<String, Int> =
        reactions.values.groupingBy { it }.eachCount()

    // Для ответа показываем короткий кусок исходного текста.
    fun replyPreview(): String = when (type) {
        MessageType.IMAGE -> "Фотография"
        MessageType.FILE -> fileName.ifBlank { "Файл" }
        MessageType.VOICE -> "Голосовое сообщение"
        MessageType.SYSTEM -> text
        MessageType.TEXT -> text
    }
}
