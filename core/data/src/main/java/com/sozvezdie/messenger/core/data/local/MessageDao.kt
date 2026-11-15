package com.sozvezdie.messenger.core.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Строка результата поиска: сообщение и название его чата. */
data class MessageSearchRow(
    @Embedded val message: MessageEntity,
    val chatTitle: String,
    val chatOtherName: String,
    val chatType: String,
)

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    fun observeMessages(chatId: String): Flow<List<MessageEntity>>

    // Поиск идёт только по сохранённой истории на телефоне.
    @Query(
        "SELECT messages.*, chats.title AS chatTitle, " +
            "chats.otherUserName AS chatOtherName, chats.type AS chatType " +
            "FROM messages INNER JOIN chats ON chats.id = messages.chatId " +
            "WHERE messages.searchText LIKE '%' || :query || '%' " +
            "ORDER BY messages.createdAt DESC LIMIT 50",
    )
    fun searchMessages(query: String): Flow<List<MessageSearchRow>>

    // REPLACE нужен потому, что одно и то же сообщение может прийти повторно
    // после переподключения к Firebase.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteChatMessages(chatId: String)

    @Query("DELETE FROM messages WHERE chatId = :chatId AND id = :messageId")
    suspend fun deleteMessage(chatId: String, messageId: String)

    // Убирает сообщения чатов, которых больше нет в списке.
    @Query("DELETE FROM messages WHERE chatId NOT IN (SELECT id FROM chats)")
    suspend fun deleteMessagesOfMissingChats()

    @Query("DELETE FROM messages")
    suspend fun clear()
}
