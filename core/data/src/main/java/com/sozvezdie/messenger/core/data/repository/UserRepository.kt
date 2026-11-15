package com.sozvezdie.messenger.core.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.sozvezdie.messenger.core.model.Presence
import com.sozvezdie.messenger.core.model.PrivacyLevel
import com.sozvezdie.messenger.core.model.PrivacySettings
import com.sozvezdie.messenger.core.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Профили пользователей, ники, приватность и присутствие «в сети».
 */
class UserRepository(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth,
) {

    // Список всех зарегистрированных пользователей: для нового диалога и групп.
    fun observeUsers(): Flow<List<User>> = callbackFlow {
        val ref = database.reference.child("users")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = snapshot.children.mapNotNull { it.toUser() }
                    .sortedBy { it.name.lowercase() }
                trySend(users)
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeProfile(uid: String): Flow<User?> = callbackFlow {
        val ref = database.reference.child("users").child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.toUser())
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observePresence(uid: String): Flow<Presence> = callbackFlow {
        val ref = database.reference.child("presence").child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val online = snapshot.child("online").getValue(Boolean::class.java) ?: false
                val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                // Если lastSeen старше 2 минут, считаем пользователя offline,
                // даже если флаг online остался true (например, приложение закрылось
                // без вызова onDisconnect).
                val fresh = lastSeen == 0L || System.currentTimeMillis() - lastSeen < 120_000L
                trySend(
                    Presence(
                        online = online && fresh,
                        lastSeen = lastSeen,
                    ),
                )
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Сохраняет профиль. Имя в уже существующих чатах тоже меняем,
     * чтобы список выглядел актуально.
     */
    suspend fun updateProfile(
        uid: String,
        name: String,
        username: String,
        about: String,
        photoUrl: String,
    ): Result<Unit> = runCatching {
        val cleanName = name.trim()
        val cleanUsername = username.trim().lowercase()
        val cleanAbout = about.trim()

        if (cleanName.length !in 2..40) error("Имя должно быть от 2 до 40 символов")
        if (cleanUsername.isNotEmpty() && !cleanUsername.matches(USERNAME_REGEX)) {
            error("Ник: 3–20 символов, латиница, цифры и _")
        }

        if (cleanUsername.isNotEmpty()) {
            val ownerUid = usernameOwner(cleanUsername)
            if (ownerUid != null && ownerUid != uid) {
                error("Этот ник уже занят")
            }
        }

        // Старый ник нужен, чтобы освободить его в справочнике.
        val oldUsername = database.reference.child("users").child(uid).child("username")
            .get().await().getValue(String::class.java).orEmpty().lowercase()

        val updates = mutableMapOf<String, Any?>(
            "users/$uid/name" to cleanName,
            "users/$uid/username" to cleanUsername,
            "users/$uid/about" to cleanAbout,
            "users/$uid/photoUrl" to photoUrl,
        )
        if (oldUsername.isNotEmpty() && oldUsername != cleanUsername) {
            updates["usernames/$oldUsername"] = null
        }
        if (cleanUsername.isNotEmpty()) {
            updates["usernames/$cleanUsername"] = uid
        }

        // Обновляем имя в названиях участников всех моих чатов.
        val chatIds = database.reference.child("userChats").child(uid).get().await()
            .children.mapNotNull { it.key }
        chatIds.forEach { chatId ->
            updates["chats/$chatId/memberNames/$uid"] = cleanName
            updates["chats/$chatId/memberPhotos/$uid"] = photoUrl
        }

        database.reference.updateChildren(updates).await()

        // Имя в Firebase Auth нужно для быстрого показа без чтения профиля.
        auth.currentUser?.let { user ->
            val request = UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()
            user.updateProfile(request).await()
        }
        Unit
    }.mapError("Не удалось сохранить профиль")

    /** Проверяет, свободен ли ник. Свой собственный ник считается свободным. */
    suspend fun isUsernameAvailable(username: String, uid: String): Boolean = runCatching {
        val owner = usernameOwner(username)
        owner == null || owner == uid
    }.getOrDefault(false)

    /** Ищет, кому уже принадлежит ник. Возвращает uid или null. */
    private suspend fun usernameOwner(username: String): String? {
        val key = username.trim().lowercase()
        if (key.isEmpty()) return null
        val users = database.reference.child("users").get().await()
        for (item in users.children) {
            val saved = item.child("username").getValue(String::class.java)
                .orEmpty()
                .lowercase()
            if (saved == key) return item.key
        }
        return null
    }

    /**
     * Есть ли у двух людей общий чат.
     * Нужно для приватности «люди из моих чатов».
     */
    suspend fun hasCommonChat(myUid: String, otherUid: String): Boolean {
        if (myUid == otherUid) return true
        return runCatching {
            val index = database.reference.child("userChats").child(myUid).get().await()
            for (chat in index.children) {
                val chatId = chat.key ?: continue
                val member = database.reference
                    .child("chats").child(chatId).child("members").child(otherUid)
                    .get().await()
                    .getValue(Boolean::class.java)
                if (member == true) return@runCatching true
            }
            false
        }.getOrDefault(false)
    }

    private fun DataSnapshot.toUser(): User? {
        val uid = child("uid").getValue(String::class.java) ?: key ?: return null
        val name = child("name").getValue(String::class.java)
            ?: child("email").getValue(String::class.java)?.substringBefore("@")
            ?: "Пользователь"
        val privacy = child("privacy")
        return User(
            uid = uid,
            email = child("email").getValue(String::class.java).orEmpty(),
            name = name,
            username = child("username").getValue(String::class.java).orEmpty(),
            photoUrl = child("photoUrl").getValue(String::class.java).orEmpty(),
            about = child("about").getValue(String::class.java).orEmpty(),
            privacy = PrivacySettings(
                email = PrivacyLevel.fromKey(
                    privacy.child("email").getValue(String::class.java),
                ),
                about = PrivacyLevel.fromKey(
                    privacy.child("about").getValue(String::class.java),
                ),
                lastSeen = PrivacyLevel.fromKey(
                    privacy.child("lastSeen").getValue(String::class.java),
                ),
            ),
        )
    }

    private fun Result<Unit>.mapError(fallback: String): Result<Unit> {
        val error = exceptionOrNull() ?: return this
        return Result.failure(Exception(error.message ?: fallback))
    }

    private companion object {
        val USERNAME_REGEX = Regex("^[a-z0-9_]{3,20}$")
    }
}

/**
 * Отмечает пользователя «в сети» и ставит обработчик onDisconnect.
 *
 * onDisconnect это специальная операция Firebase: сервер сам запишет
 * offline, если телефон потеряет связь или приложение закроется.
 */
class PresenceManager(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
) {
    private var lastUid: String? = null

    fun start() {
        auth.addAuthStateListener {
            val uid = auth.currentUser?.uid
            if (uid != lastUid) {
                // Предыдущий пользователь вышел, сразу помечаем его offline.
                lastUid?.let { oldUid -> setOffline(oldUid) }
                lastUid = uid
            }
        }

        // .info/connected это встроенный узел Firebase. Он приходит заново
        // после каждого восстановления интернета.
        val connectedRef = database.reference.child(".info").child("connected")
        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.getValue(Boolean::class.java) != true) return
                auth.currentUser?.uid?.let { setOnline(it) }
            }

            override fun onCancelled(error: DatabaseError) = Unit
        })
    }

    /** Вызываем, когда приложение снова на экране. */
    fun onAppForeground() {
        auth.currentUser?.uid?.let { setOnline(it) }
    }

    /** Вызываем, когда пользователь свернул приложение. */
    fun onAppBackground() {
        auth.currentUser?.uid?.let { setOffline(it) }
    }

    private fun setOnline(uid: String) {
        val presenceRef: DatabaseReference = database.reference.child("presence").child(uid)
        presenceRef.onDisconnect().setValue(
            mapOf(
                "online" to false,
                "lastSeen" to ServerValue.TIMESTAMP,
            ),
        )
        presenceRef.child("online").setValue(true)
        presenceRef.child("lastSeen").setValue(ServerValue.TIMESTAMP)
    }

    private fun setOffline(uid: String) {
        database.reference.child("presence").child(uid).setValue(
            mapOf(
                "online" to false,
                "lastSeen" to ServerValue.TIMESTAMP,
            ),
        )
    }
}
