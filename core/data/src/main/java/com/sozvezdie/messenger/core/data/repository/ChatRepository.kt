package com.sozvezdie.messenger.core.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.sozvezdie.messenger.core.data.local.ChatDao
import com.sozvezdie.messenger.core.data.local.ChatEntity
import com.sozvezdie.messenger.core.data.local.MessageDao
import com.sozvezdie.messenger.core.data.local.toModel
import com.sozvezdie.messenger.core.model.Chat
import com.sozvezdie.messenger.core.model.ChatRole
import com.sozvezdie.messenger.core.model.ChatType
import com.sozvezdie.messenger.core.model.GroupInvite
import com.sozvezdie.messenger.core.model.MessageType
import com.sozvezdie.messenger.core.model.User
import com.sozvezdie.messenger.core.model.normalizeGroupLink
import com.sozvezdie.messenger.core.model.normalizeSearch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

/** Что получилось после создания группы. Эти данные нужны для окна приглашения. */
data class CreatedGroup(
    val chatId: String,
    val title: String,
    val link: String,
    val invitedNames: List<String>,
)

/**
 * Чаты: личные диалоги и группы, участники, роли, «прочитано» и «печатает».
 *
 * Схема в Realtime Database:
 *   userChats/{uid}/{chatId} = true    быстрый список чатов пользователя
 *   chats/{chatId}/type                direct или group
 *   chats/{chatId}/title               название группы
 *   chats/{chatId}/link                короткий код группы
 *   chats/{chatId}/members             участники
 *   chats/{chatId}/memberNames         имена для заголовка
 *   chats/{chatId}/admins              администраторы группы
 *   chats/{chatId}/lastMessage         превью для списка
 *   chats/{chatId}/readAt/{uid}        до какого времени прочитано
 *   typing/{chatId}/{uid}              кто сейчас печатает
 *   groupLinks/{link}                  chatId, чтобы найти группу по коду
 *   groupInvites/{uid}/{chatId}        приглашения человека в группы
 */
class ChatRepository(
    private val database: FirebaseDatabase,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
) {

    /**
     * Список чатов. Firebase присылает изменения, мы складываем их в Room,
     * а наружу отдаём поток из Room. Поэтому история видна и без сети.
     */
    fun observeChats(myUid: String): Flow<List<Chat>> = callbackFlow {
        val listeners = mutableMapOf<String, Pair<DatabaseReference, ValueEventListener>>()

        fun attachChat(chatId: String) {
            val ref = database.reference.child("chats").child(chatId)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val entity = snapshot.toChatEntity(myUid)
                    if (entity == null) {
                        launch { chatDao.delete(chatId) }
                        // Если чат удалили, убираем его и из своего списка чатов.
                        launch {
                            runCatching {
                                database.reference.child("userChats").child(myUid)
                                    .child(chatId).removeValue().await()
                            }
                        }
                    } else {
                        launch { chatDao.upsert(listOf(entity)) }
                    }
                }

                override fun onCancelled(error: DatabaseError) = Unit
            }
            ref.addValueEventListener(listener)
            listeners[chatId] = ref to listener
        }

        fun detachChat(chatId: String) {
            val pair = listeners.remove(chatId) ?: return
            pair.first.removeEventListener(pair.second)
        }

        val indexRef = database.reference.child("userChats").child(myUid)
        val indexListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ids = snapshot.children.mapNotNull { it.key }.toSet()
                (listeners.keys - ids).forEach { chatId ->
                    detachChat(chatId)
                    launch { chatDao.delete(chatId) }
                }
                // Чистим кэш от чатов, которые удалили, пока список был закрыт.
                launch {
                    if (ids.isEmpty()) chatDao.clear() else chatDao.deleteNotIn(ids.toList())
                    messageDao.deleteMessagesOfMissingChats()
                }
                (ids - listeners.keys).forEach { attachChat(it) }
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        indexRef.addValueEventListener(indexListener)

        val roomJob = launch {
            chatDao.observeChats().collect { entities ->
                send(entities.map { it.toModel() })
            }
        }

        awaitClose {
            indexRef.removeEventListener(indexListener)
            listeners.keys.toList().forEach { detachChat(it) }
            roomJob.cancel()
        }
    }

    /** Поиск по уже сохранённым чатам. Работает без интернета. */
    fun searchChats(query: String): Flow<List<Chat>> =
        chatDao.searchChats(normalizeSearch(query)).map { chats -> chats.map { it.toModel() } }

    // Один чат в реальном времени: нужен для шапки, участников и галочек.
    fun observeChat(chatId: String, myUid: String): Flow<Chat?> = callbackFlow {
        val ref = database.reference.child("chats").child(chatId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }
                val chat = snapshot.toChat(myUid)
                // Если пользователя удалили из группы, чат для него закрыт.
                if (chat == null || myUid !in chat.members) {
                    trySend(null)
                } else {
                    trySend(chat)
                }
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Открывает личный чат с пользователем или создаёт его.
     *
     * id чата собирается из двух uid по алфавиту: так у обоих
     * пользователей получится один и тот же чат, а не два разных.
     */
    suspend fun openChat(
        myUid: String,
        myName: String,
        myPhotoUrl: String,
        other: User,
    ): Result<String> = runCatching {
        val chatId = listOf(myUid, other.uid).sorted().joinToString("__")

        database.reference.updateChildren(
            mapOf(
                "chats/$chatId/id" to chatId,
                "chats/$chatId/type" to ChatType.toKey(ChatType.DIRECT),
                "chats/$chatId/title" to "",
                "chats/$chatId/photoUrl" to "",
                "chats/$chatId/ownerId" to "",
                "chats/$chatId/createdAt" to ServerValue.TIMESTAMP,
                "chats/$chatId/members/$myUid" to true,
                "chats/$chatId/members/${other.uid}" to true,
                "chats/$chatId/memberNames/$myUid" to myName,
                "chats/$chatId/memberNames/${other.uid}" to other.name,
                "chats/$chatId/memberPhotos/$myUid" to myPhotoUrl,
                "chats/$chatId/memberPhotos/${other.uid}" to other.photoUrl,
                "userChats/$myUid/$chatId" to true,
                "userChats/${other.uid}/$chatId" to true,
            ),
        ).await()
        chatId
    }.mapError("Не удалось открыть чат")

    /**
     * Создаёт группу и отправляет приглашения выбранным людям.
     *
     * Сначала в группе только владелец. Остальные получают приглашение
     * и входят по ссылке сами. Так ссылку можно отозвать, пересоздав её.
     */
    suspend fun createGroup(
        myUid: String,
        myName: String,
        myPhotoUrl: String,
        title: String,
        photoUrl: String,
        members: List<User>,
    ): Result<CreatedGroup> = runCatching {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) error("Введите название группы")
        if (cleanTitle.length > MAX_TITLE_LENGTH) {
            error("Название группы не длиннее $MAX_TITLE_LENGTH символов")
        }

        val invited = members
            .filter { it.uid != myUid }
            .distinctBy { it.uid }
        if (invited.isEmpty()) error("Выберите хотя бы одного участника")
        if (invited.size + 1 > MAX_MEMBERS) {
            error("В группе может быть не больше $MAX_MEMBERS человек")
        }

        val link = generateUniqueLink()
        val chatId = database.reference.child("chats").push().key
            ?: error("Не удалось создать группу")

        // Первым шагом создаём группу только с владельцем.
        val updates = mutableMapOf<String, Any?>(
            "chats/$chatId/id" to chatId,
            "chats/$chatId/type" to ChatType.toKey(ChatType.GROUP),
            "chats/$chatId/title" to cleanTitle,
            "chats/$chatId/photoUrl" to photoUrl,
            "chats/$chatId/ownerId" to myUid,
            "chats/$chatId/createdAt" to ServerValue.TIMESTAMP,
            "chats/$chatId/updatedAt" to ServerValue.TIMESTAMP,
            "chats/$chatId/members/$myUid" to true,
            "chats/$chatId/memberNames/$myUid" to myName,
            "chats/$chatId/memberPhotos/$myUid" to myPhotoUrl,
            "chats/$chatId/admins/$myUid" to true,
            "userChats/$myUid/$chatId" to true,
        )
        database.reference.updateChildren(updates).await()

        // Вторым шагом пишем ссылку, приглашения и системное сообщение.
        // Правила Firebase разрешают это, когда группа уже существует.
        val secondUpdates = systemMessageUpdates(
            chatId = chatId,
            senderId = myUid,
            senderName = myName,
            senderPhotoUrl = myPhotoUrl,
            text = "Группа создана",
        ).toMutableMap()
        secondUpdates["chats/$chatId/link"] = link
        secondUpdates["groupLinks/$link"] = chatId
        invited.forEach { member ->
            secondUpdates["groupInvites/${member.uid}/$chatId"] = mapOf(
                "chatId" to chatId,
                "title" to cleanTitle,
                "photoUrl" to photoUrl,
                "link" to link,
                "fromUid" to myUid,
                "fromName" to myName,
                "createdAt" to ServerValue.TIMESTAMP,
            )
        }

        try {
            database.reference.updateChildren(secondUpdates).await()
        } catch (error: Exception) {
            // Если ссылку или приглашения записать не удалось,
            // убираем только что созданную группу, чтобы не оставлять пустую.
            runCatching {
                database.reference.updateChildren(
                    mapOf(
                        "chats/$chatId" to null,
                        "messages/$chatId" to null,
                        "userChats/$myUid/$chatId" to null,
                    ),
                ).await()
            }
            throw error
        }

        CreatedGroup(
            chatId = chatId,
            title = cleanTitle,
            link = link,
            invitedNames = invited.map { it.name },
        )
    }.mapError("Не удалось создать группу")

    /**
     * Создаёт новую ссылку группы. Старая ссылка перестаёт работать.
     * Менять ссылку может только владелец или администратор.
     */
    suspend fun regenerateGroupLink(chatId: String, myUid: String): Result<String> = runCatching {
        val snapshot = database.reference.child("chats").child(chatId).get().await()
        if (!snapshot.exists()) error("Группа не найдена")

        val ownerId = snapshot.child("ownerId").getValue(String::class.java).orEmpty()
        val admins = snapshot.child("admins").children.mapNotNull { it.key }.toSet()
        if (ownerId != myUid && myUid !in admins) {
            error("Ссылку может менять только владелец или администратор")
        }

        val oldLink = snapshot.child("link").getValue(String::class.java).orEmpty()
        val newLink = generateUniqueLink()

        val updates = mutableMapOf<String, Any?>(
            "chats/$chatId/link" to newLink,
            "groupLinks/$newLink" to chatId,
        )
        if (oldLink.isNotBlank() && oldLink != newLink) {
            updates["groupLinks/$oldLink"] = null
        }
        database.reference.updateChildren(updates).await()
        newLink
    }.mapError("Не удалось обновить ссылку группы")

    /** Приглашения текущего пользователя. */
    fun observeGroupInvites(uid: String): Flow<List<GroupInvite>> = callbackFlow {
        val ref = database.reference.child("groupInvites").child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val invites = snapshot.children.mapNotNull { child ->
                    val chatId = child.child("chatId").getValue(String::class.java)
                        ?: child.key
                        ?: return@mapNotNull null
                    GroupInvite(
                        chatId = chatId,
                        title = child.child("title").getValue(String::class.java).orEmpty(),
                        photoUrl = child.child("photoUrl").getValue(String::class.java).orEmpty(),
                        link = child.child("link").getValue(String::class.java).orEmpty(),
                        fromUid = child.child("fromUid").getValue(String::class.java).orEmpty(),
                        fromName = child.child("fromName").getValue(String::class.java).orEmpty(),
                        createdAt = child.child("createdAt").getValue(Long::class.java) ?: 0L,
                    )
                }.sortedByDescending { it.createdAt }
                trySend(invites)
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Вход в группу по короткой ссылке или коду. */
    suspend fun joinGroupByLink(
        input: String,
        myUid: String,
        myName: String,
        myPhotoUrl: String,
    ): Result<String> = runCatching {
        val code = normalizeGroupLink(input)
        if (code.isEmpty()) error("Введите ссылку или код группы")
        // Ссылка хранится в отдельном узле groupLinks: по коду находим id чата.
        val chatId = database.reference.child("groupLinks").child(code).get().await()
            .getValue(String::class.java)
            ?: error("Группа не найдена")
        joinGroup(chatId, myUid, myName, myPhotoUrl)
        chatId
    }.mapError("Не удалось присоединиться к группе")

    /**
     * Принимает приглашение из списка чатов.
     *
     * Ссылка проверяется заново. Если владелец её пересоздал, старая ссылка
     * уже не сработает. Это и есть отзыв приглашения.
     */
    suspend fun joinGroupByInvite(
        invite: GroupInvite,
        myUid: String,
        myName: String,
        myPhotoUrl: String,
    ): Result<String> = runCatching {
        val code = normalizeGroupLink(invite.link)
        if (code.isEmpty()) error("Ссылка приглашения недействительна")

        val chatId = database.reference.child("groupLinks").child(code).get().await()
            .getValue(String::class.java)
        if (chatId == null) {
            // Ссылку пересоздали или группу удалили: приглашение больше не нужно.
            runCatching {
                database.reference.child("groupInvites").child(myUid).child(invite.chatId)
                    .removeValue().await()
            }
            error("Ссылка приглашения больше не работает")
        }

        joinGroup(chatId, myUid, myName, myPhotoUrl)
        chatId
    }.mapError("Не удалось присоединиться к группе")

    /** Убирает приглашение из списка пользователя. */
    suspend fun declineGroupInvite(uid: String, chatId: String): Result<Unit> = runCatching {
        database.reference.child("groupInvites").child(uid).child(chatId)
            .removeValue().await()
        Unit
    }.mapError("Не удалось отклонить приглашение")

    /** Меняет название и фото группы. */
    suspend fun updateGroup(
        chatId: String,
        myUid: String,
        myName: String,
        myPhotoUrl: String,
        title: String,
        photoUrl: String,
    ): Result<Unit> = runCatching {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) error("Введите название группы")
        if (cleanTitle.length > MAX_TITLE_LENGTH) {
            error("Название группы не длиннее $MAX_TITLE_LENGTH символов")
        }

        val updates = systemMessageUpdates(
            chatId = chatId,
            senderId = myUid,
            senderName = myName,
            senderPhotoUrl = myPhotoUrl,
            text = "Название группы изменено",
        ).toMutableMap()
        updates["chats/$chatId/title"] = cleanTitle
        updates["chats/$chatId/photoUrl"] = photoUrl
        updates["chats/$chatId/updatedAt"] = ServerValue.TIMESTAMP
        database.reference.updateChildren(updates).await()
        Unit
    }.mapError("Не удалось изменить группу")

    /** Добавляет участников. Права проверяют правила Firebase. */
    suspend fun addMembers(
        chatId: String,
        myUid: String,
        myName: String,
        myPhotoUrl: String,
        users: List<User>,
    ): Result<Unit> = runCatching {
        if (users.isEmpty()) return@runCatching
        val current = database.reference.child("chats").child(chatId).get().await()
        val currentCount = current.child("members").childrenCount.toInt()
        if (currentCount + users.size > MAX_MEMBERS) {
            error("В группе может быть не больше $MAX_MEMBERS человек")
        }

        val updates = systemMessageUpdates(
            chatId = chatId,
            senderId = myUid,
            senderName = myName,
            senderPhotoUrl = myPhotoUrl,
            text = if (users.size == 1) {
                "${users.first().name} добавлен(а) в группу"
            } else {
                "Добавлены участники: ${users.joinToString { it.name }}"
            },
        ).toMutableMap()

        users.forEach { user ->
            updates["chats/$chatId/members/${user.uid}"] = true
            updates["chats/$chatId/memberNames/${user.uid}"] = user.name
            updates["chats/$chatId/memberPhotos/${user.uid}"] = user.photoUrl
            updates["userChats/${user.uid}/$chatId"] = true
        }
        updates["chats/$chatId/updatedAt"] = ServerValue.TIMESTAMP

        database.reference.updateChildren(updates).await()
        Unit
    }.mapError("Не удалось добавить участников")

    /** Удаляет участника. Владельца удалить нельзя. */
    suspend fun removeMember(
        chatId: String,
        myUid: String,
        myName: String,
        myPhotoUrl: String,
        target: User,
    ): Result<Unit> = runCatching {
        val chat = database.reference.child("chats").child(chatId).get().await()
        val ownerId = chat.child("ownerId").getValue(String::class.java).orEmpty()
        if (ownerId == target.uid) error("Нельзя удалить владельца группы")

        sendSystemMessage(
            chatId = chatId,
            senderId = myUid,
            senderName = myName,
            senderPhotoUrl = myPhotoUrl,
            text = "${target.name} удалён(а) из группы",
        )

        database.reference.updateChildren(
            mapOf(
                "chats/$chatId/members/${target.uid}" to null,
                "chats/$chatId/memberNames/${target.uid}" to null,
                "chats/$chatId/memberPhotos/${target.uid}" to null,
                "chats/$chatId/admins/${target.uid}" to null,
                "chats/$chatId/readAt/${target.uid}" to null,
                "userChats/${target.uid}/$chatId" to null,
                "chats/$chatId/updatedAt" to ServerValue.TIMESTAMP,
            ),
        ).await()
        Unit
    }.mapError("Не удалось удалить участника")

    /** Назначает или снимает администратора. Это делает только владелец. */
    suspend fun setAdmin(
        chatId: String,
        myUid: String,
        myName: String,
        myPhotoUrl: String,
        target: User,
        admin: Boolean,
    ): Result<Unit> = runCatching {
        val updates = systemMessageUpdates(
            chatId = chatId,
            senderId = myUid,
            senderName = myName,
            senderPhotoUrl = myPhotoUrl,
            text = if (admin) {
                "${target.name} теперь администратор"
            } else {
                "${target.name} больше не администратор"
            },
        ).toMutableMap()
        updates["chats/$chatId/admins/${target.uid}"] = if (admin) true else null
        database.reference.updateChildren(updates).await()
        Unit
    }.mapError("Не удалось изменить права")

    /** Передаёт права владельца другому участнику. */
    suspend fun transferOwnership(
        chatId: String,
        myUid: String,
        myName: String,
        myPhotoUrl: String,
        target: User,
    ): Result<Unit> = runCatching {
        val updates = systemMessageUpdates(
            chatId = chatId,
            senderId = myUid,
            senderName = myName,
            senderPhotoUrl = myPhotoUrl,
            text = "${target.name} стал(а) владельцем группы",
        ).toMutableMap()
        updates["chats/$chatId/ownerId"] = target.uid
        updates["chats/$chatId/admins/${target.uid}"] = true
        updates["chats/$chatId/admins/$myUid"] = true
        database.reference.updateChildren(updates).await()
        Unit
    }.mapError("Не удалось передать права")

    /** Выход из группы. Владелец сначала должен передать права. */
    suspend fun leaveGroup(
        chatId: String,
        myUid: String,
        myName: String,
        myPhotoUrl: String,
    ): Result<Unit> = runCatching {
        val chat = database.reference.child("chats").child(chatId).get().await()
        val ownerId = chat.child("ownerId").getValue(String::class.java).orEmpty()
        if (ownerId == myUid) {
            error("Сначала передайте права владельца другому участнику")
        }

        sendSystemMessage(
            chatId = chatId,
            senderId = myUid,
            senderName = myName,
            senderPhotoUrl = myPhotoUrl,
            text = "$myName покинул(а) группу",
        )

        database.reference.updateChildren(
            mapOf(
                "chats/$chatId/members/$myUid" to null,
                "chats/$chatId/memberNames/$myUid" to null,
                "chats/$chatId/memberPhotos/$myUid" to null,
                "chats/$chatId/admins/$myUid" to null,
                "chats/$chatId/readAt/$myUid" to null,
                "userChats/$myUid/$chatId" to null,
                "chats/$chatId/updatedAt" to ServerValue.TIMESTAMP,
            ),
        ).await()
        Unit
    }.mapError("Не удалось выйти из группы")

    /** Удаляет группу целиком. Это может только владелец. */
    suspend fun deleteGroup(chatId: String, ownerUid: String): Result<Unit> = runCatching {
        val chat = database.reference.child("chats").child(chatId).get().await()
        val realOwner = chat.child("ownerId").getValue(String::class.java).orEmpty()
        if (realOwner != ownerUid) error("Удалить группу может только владелец")

        // Историю удаляем первой, пока чат ещё существует:
        // правила Firebase проверяют, что человек был участником.
        runCatching {
            database.reference.child("messages").child(chatId).removeValue().await()
        }

        // Сначала убираем короткую ссылку, пока группа ещё существует:
        // правило Firebase ищет владельца через chats/{chatId}.
        val link = chat.child("link").getValue(String::class.java).orEmpty()
        if (link.isNotBlank()) {
            runCatching {
                database.reference.child("groupLinks").child(link).removeValue().await()
            }
        }

        // Теперь убираем саму группу и свою строку в списке чатов.
        // Чужие списки трогать нельзя: Firebase разрешает менять только свой.
        database.reference.updateChildren(
            mapOf(
                "chats/$chatId" to null,
                "userChats/$ownerUid/$chatId" to null,
            ),
        ).await()

        // Метку «печатает» тоже убираем. Если Firebase не разрешит,
        // группа всё равно уже удалена.
        runCatching {
            database.reference.child("typing").child(chatId).removeValue().await()
        }
        Unit
    }.mapError("Не удалось удалить группу")

    // Отметка прочтения: время ставит сервер Firebase, чтобы часы телефонов не влияли.
    suspend fun markRead(chatId: String, uid: String) {
        database.reference
            .child("chats").child(chatId).child("readAt").child(uid)
            .setValue(ServerValue.TIMESTAMP)
            .await()
    }

    suspend fun setTyping(chatId: String, uid: String, name: String) {
        val ref = database.reference.child("typing").child(chatId).child(uid)
        ref.setValue(
            mapOf(
                "name" to name,
                "at" to ServerValue.TIMESTAMP,
            ),
        ).await()
        // Если приложение закроется, надпись «печатает» исчезнет сама.
        ref.onDisconnect().removeValue()
    }

    suspend fun clearTyping(chatId: String, uid: String) {
        database.reference.child("typing").child(chatId).child(uid).removeValue().await()
    }

    fun observeTyping(chatId: String, myUid: String): Flow<List<String>> = callbackFlow {
        val ref = database.reference.child("typing").child(chatId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val now = System.currentTimeMillis()
                val names = snapshot.children.mapNotNull { child ->
                    if (child.key == myUid) return@mapNotNull null

                    // Старые записи были просто строкой с именем.
                    // getValue(String::class.java) бросает исключение, если в узле лежит объект,
                    // поэтому ловим его и читаем поле "name".
                    val name = try {
                        child.getValue(String::class.java)
                    } catch (e: Exception) {
                        null
                    } ?: child.child("name").getValue(String::class.java)
                    val at = child.child("at").getValue(Long::class.java) ?: 0L
                    val fresh = at == 0L || now - at < TYPING_LIFETIME_MS
                    if (name.isNullOrBlank() || !fresh) null else name
                }
                trySend(names)
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /** Подбирает короткий код, которого ещё нет в groupLinks. */
    private suspend fun generateUniqueLink(): String {
        repeat(MAX_LINK_ATTEMPTS) {
            val code = buildString {
                append("group")
                repeat(LINK_RANDOM_LENGTH) {
                    append(LINK_ALPHABET[Random.nextInt(LINK_ALPHABET.length)])
                }
            }
            val exists = database.reference.child("groupLinks").child(code)
                .get().await().exists()
            if (!exists) return code
        }
        error("Не удалось подобрать ссылку группы")
    }

    /**
     * Добавляет человека в группу.
     *
     * Сначала записываем участие, потом системное сообщение: правила Firebase
     * пускают в messages только тех, кто уже состоит в чате.
     */
    private suspend fun joinGroup(
        chatId: String,
        myUid: String,
        myName: String,
        myPhotoUrl: String,
    ) {
        val snapshot = database.reference.child("chats").child(chatId).get().await()
        if (!snapshot.exists()) error("Группа не найдена")

        if (snapshot.child("members").child(myUid)
                .getValue(Boolean::class.java) == true
        ) {
            // Человек уже в группе. Приглашение на всякий случай убираем.
            runCatching {
                database.reference.child("groupInvites").child(myUid).child(chatId)
                    .removeValue().await()
            }
            return
        }

        val count = snapshot.child("members").childrenCount.toInt()
        if (count >= MAX_MEMBERS) error("В группе уже $MAX_MEMBERS участников")

        database.reference.updateChildren(
            mapOf(
                "chats/$chatId/members/$myUid" to true,
                "chats/$chatId/memberNames/$myUid" to myName,
                "chats/$chatId/memberPhotos/$myUid" to myPhotoUrl,
                "userChats/$myUid/$chatId" to true,
                "chats/$chatId/updatedAt" to ServerValue.TIMESTAMP,
            ),
        ).await()

        // Если сообщение не отправится, вход в группу всё равно уже состоялся.
        runCatching {
            sendSystemMessage(
                chatId = chatId,
                senderId = myUid,
                senderName = myName,
                senderPhotoUrl = myPhotoUrl,
                text = "$myName присоединился(ась) по ссылке",
            )
        }

        // Приглашение больше не нужно.
        runCatching {
            database.reference.child("groupInvites").child(myUid).child(chatId)
                .removeValue().await()
        }
    }

    /** Отправляет системное сообщение и обновляет превью чата. */
    private suspend fun sendSystemMessage(
        chatId: String,
        senderId: String,
        senderName: String,
        senderPhotoUrl: String,
        text: String,
    ) {
        database.reference.updateChildren(
            systemMessageUpdates(chatId, senderId, senderName, senderPhotoUrl, text),
        ).await()
    }

    private fun systemMessageUpdates(
        chatId: String,
        senderId: String,
        senderName: String,
        senderPhotoUrl: String,
        text: String,
    ): Map<String, Any?> {
        val id = database.reference.child("messages").child(chatId).push().key
            ?: error("Не удалось создать сообщение")
        return mapOf(
            "messages/$chatId/$id" to mapOf(
                "id" to id,
                "senderId" to senderId,
                "senderName" to senderName,
                "senderPhotoUrl" to senderPhotoUrl,
                "type" to MessageType.toKey(MessageType.SYSTEM),
                "text" to text,
                "createdAt" to ServerValue.TIMESTAMP,
            ),
            "chats/$chatId/lastMessage/text" to text,
            "chats/$chatId/lastMessage/senderId" to senderId,
            "chats/$chatId/lastMessage/senderName" to senderName,
            "chats/$chatId/lastMessage/type" to MessageType.toKey(MessageType.SYSTEM),
            "chats/$chatId/lastMessage/createdAt" to ServerValue.TIMESTAMP,
            "chats/$chatId/updatedAt" to ServerValue.TIMESTAMP,
        )
    }

    private fun DataSnapshot.toChatEntity(myUid: String): ChatEntity? {
        val id = child("id").getValue(String::class.java) ?: key ?: return null
        val type = ChatType.fromKey(child("type").getValue(String::class.java))
        val members = child("members").children.mapNotNull { it.key }.toSet()
        if (members.isEmpty()) return null

        val memberNames = child("memberNames").children.associate { item ->
            item.key.orEmpty() to item.getValue(String::class.java).orEmpty()
        }
        val memberPhotos = child("memberPhotos").children.associate { item ->
            item.key.orEmpty() to item.getValue(String::class.java).orEmpty()
        }
        val otherUid = if (type == ChatType.DIRECT) {
            members.firstOrNull { it != myUid }.orEmpty()
        } else {
            ""
        }
        val otherName = if (type == ChatType.DIRECT) {
            memberNames[otherUid] ?: "Пользователь"
        } else {
            ""
        }
        val title = child("title").getValue(String::class.java).orEmpty()
        val link = child("link").getValue(String::class.java).orEmpty()
        val ownerId = child("ownerId").getValue(String::class.java).orEmpty()
        val admins = child("admins").children.mapNotNull { it.key }.toSet()
        val readAt = child("readAt").children.associate { item ->
            item.key.orEmpty() to (item.getValue(Long::class.java) ?: 0L)
        }

        val last = child("lastMessage")
        val lastText = last.child("text").getValue(String::class.java).orEmpty()
        val lastSender = last.child("senderId").getValue(String::class.java).orEmpty()
        val lastSenderName = last.child("senderName").getValue(String::class.java).orEmpty()
        val lastAt = last.child("createdAt").getValue(Long::class.java) ?: 0L
        val lastType = MessageType.fromKey(last.child("type").getValue(String::class.java))

        val myRole = when {
            ownerId == myUid -> ChatRole.OWNER
            myUid in admins -> ChatRole.ADMIN
            else -> ChatRole.MEMBER
        }
        val otherReadAt = if (type == ChatType.DIRECT) {
            readAt[otherUid] ?: 0L
        } else {
            readAt.filterKeys { it != myUid }.values.minOrNull() ?: 0L
        }

        val searchText = normalizeSearch(
            listOf(
                title,
                link,
                otherName,
                memberNames.values.joinToString(" "),
                lastText,
            ).joinToString(" "),
        )

        return ChatEntity(
            id = id,
            type = ChatType.toKey(type),
            title = title,
            link = link,
            photoUrl = child("photoUrl").getValue(String::class.java).orEmpty(),
            ownerId = ownerId,
            otherUserId = otherUid,
            otherUserName = otherName,
            otherUserPhotoUrl = memberPhotos[otherUid].orEmpty(),
            lastMessageText = lastText,
            lastMessageAt = lastAt,
            lastMessageSenderId = lastSender,
            lastMessageSenderName = lastSenderName,
            lastMessageType = MessageType.toKey(lastType),
            myReadAt = readAt[myUid] ?: 0L,
            otherReadAt = otherReadAt,
            memberCount = members.size,
            myRole = myRole.name,
            searchText = searchText,
        )
    }

    private fun DataSnapshot.toChat(myUid: String): Chat? {
        val id = child("id").getValue(String::class.java) ?: key ?: return null
        val type = ChatType.fromKey(child("type").getValue(String::class.java))
        val members = child("members").children.mapNotNull { it.key }.toSet()
        if (members.isEmpty()) return null

        val memberNames = child("memberNames").children.associate { item ->
            item.key.orEmpty() to item.getValue(String::class.java).orEmpty()
        }
        val memberPhotos = child("memberPhotos").children.associate { item ->
            item.key.orEmpty() to item.getValue(String::class.java).orEmpty()
        }
        val otherUid = if (type == ChatType.DIRECT) {
            members.firstOrNull { it != myUid }.orEmpty()
        } else {
            ""
        }
        val title = child("title").getValue(String::class.java).orEmpty()
        val link = child("link").getValue(String::class.java).orEmpty()
        val ownerId = child("ownerId").getValue(String::class.java).orEmpty()
        val admins = child("admins").children.mapNotNull { it.key }.toSet()
        val readAt = child("readAt").children.associate { item ->
            item.key.orEmpty() to (item.getValue(Long::class.java) ?: 0L)
        }

        val last = child("lastMessage")
        val lastText = last.child("text").getValue(String::class.java).orEmpty()
        val lastSender = last.child("senderId").getValue(String::class.java).orEmpty()
        val lastSenderName = last.child("senderName").getValue(String::class.java).orEmpty()
        val lastAt = last.child("createdAt").getValue(Long::class.java) ?: 0L
        val lastType = MessageType.fromKey(last.child("type").getValue(String::class.java))

        val myRole = when {
            ownerId == myUid -> ChatRole.OWNER
            myUid in admins -> ChatRole.ADMIN
            else -> ChatRole.MEMBER
        }
        val otherReadAt = if (type == ChatType.DIRECT) {
            readAt[otherUid] ?: 0L
        } else {
            readAt.filterKeys { it != myUid }.values.minOrNull() ?: 0L
        }
        val searchText = normalizeSearch(
            listOf(
                title,
                link,
                memberNames[otherUid].orEmpty(),
                memberNames.values.joinToString(" "),
                lastText,
            ).joinToString(" "),
        )

        return Chat(
            id = id,
            type = type,
            title = title,
            link = link,
            photoUrl = child("photoUrl").getValue(String::class.java).orEmpty(),
            ownerId = ownerId,
            otherUserId = otherUid,
            otherUserName = memberNames[otherUid].orEmpty(),
            otherUserPhotoUrl = memberPhotos[otherUid].orEmpty(),
            members = members,
            memberNames = memberNames,
            memberPhotos = memberPhotos,
            admins = admins,
            readAt = readAt,
            lastMessageText = lastText,
            lastMessageAt = lastAt,
            lastMessageSenderId = lastSender,
            lastMessageSenderName = lastSenderName,
            lastMessageType = lastType,
            myReadAt = readAt[myUid] ?: 0L,
            otherReadAt = otherReadAt,
            memberCount = members.size,
            myRole = myRole,
            searchText = searchText,
        )
    }

    private fun <T> Result<T>.mapError(fallback: String): Result<T> {
        val error = exceptionOrNull() ?: return this
        return Result.failure(Exception(error.message ?: fallback))
    }

    private companion object {
        const val MAX_MEMBERS = 50
        const val MAX_TITLE_LENGTH = 60
        const val TYPING_LIFETIME_MS = 5_000L

        // В коротком коде нет похожих символов вроде 0 и O, чтобы ссылку
        // было проще диктовать и вводить вручную.
        const val LINK_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
        const val LINK_RANDOM_LENGTH = 4
        const val MAX_LINK_ATTEMPTS = 8
    }
}
