package com.sozvezdie.messenger.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    // Flow сам присылает новый список, когда таблица меняется.
    @Query("SELECT * FROM chats ORDER BY lastMessageAt DESC")
    fun observeChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId")
    fun observeChat(chatId: String): Flow<ChatEntity?>

    // Поиск по названию, имени собеседника и последнему сообщению.
    @Query(
        "SELECT * FROM chats WHERE searchText LIKE '%' || :query || '%' " +
            "ORDER BY lastMessageAt DESC LIMIT 20",
    )
    fun searchChats(query: String): Flow<List<ChatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chats: List<ChatEntity>)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun delete(chatId: String)

    // Убирает чаты, которых больше нет в списке пользователя.
    @Query("DELETE FROM chats WHERE id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    @Query("DELETE FROM chats")
    suspend fun clear()
}
