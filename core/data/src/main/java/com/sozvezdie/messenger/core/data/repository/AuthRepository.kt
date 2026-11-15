package com.sozvezdie.messenger.core.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Вход, регистрация и выход.
 *
 * Firebase сам хранит сессию на телефоне, поэтому после перезапуска
 * приложение сразу открывает список чатов, если пользователь уже вошёл.
 */
class AuthRepository(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
    private val clearLocalHistory: suspend () -> Unit,
) {

    val currentUserId: String? get() = auth.currentUser?.uid

    // Поток id пользователя для главного экрана: сразу знает текущее
    // состояние и меняется после входа или выхода.
    private val _currentUserIdFlow = MutableStateFlow(auth.currentUser?.uid)
    val currentUserIdFlow: StateFlow<String?> = _currentUserIdFlow.asStateFlow()

    init {
        auth.addAuthStateListener { _currentUserIdFlow.value = it.currentUser?.uid }
    }

    val currentUserName: String
        get() = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email?.substringBefore("@")
            ?: "Пользователь"

    // Поток состояния входа: экраны сами решают, показать вход или чаты.
    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        // Если профиль когда-то не записался, создаём его при входе.
        ensureProfile()
    }.mapError()

    suspend fun signUp(
        name: String,
        username: String,
        email: String,
        password: String,
    ): Result<Unit> = runCatching {
        val cleanName = name.trim()
        val cleanUsername = username.trim().lowercase()
        val cleanEmail = email.trim()

        if (cleanName.length !in 2..40) error("Имя должно быть от 2 до 40 символов")
        if (!cleanUsername.matches(USERNAME_REGEX)) {
            error("Ник: 3–20 символов, латиница, цифры и _")
        }

        // Ник должен быть свободен. Проверяем до создания аккаунта.
        if (isUsernameTaken(cleanUsername)) error("Этот ник уже занят")

        val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
        val user = result.user ?: error("Firebase не вернул пользователя")

        val profile = UserProfileChangeRequest.Builder()
            .setDisplayName(cleanName)
            .build()
        user.updateProfile(profile).await()

        val profileData = mapOf(
            "uid" to user.uid,
            "email" to cleanEmail,
            "name" to cleanName,
            "username" to cleanUsername,
            "photoUrl" to "",
            "about" to "",
            "createdAt" to ServerValue.TIMESTAMP,
            "privacy" to mapOf(
                "email" to "all",
                "about" to "all",
                "lastSeen" to "all",
            ),
        )
        try {
            // Профиль и запись в справочнике ников сохраняем одной операцией.
            // Справочник читается даже без входа, поэтому ник можно проверить
            // до создания аккаунта.
            database.reference.updateChildren(
                mapOf(
                    "users/${user.uid}" to profileData,
                    "usernames/$cleanUsername" to user.uid,
                ),
            ).await()
        } catch (error: Exception) {
            // Ник могли занять в ту же секунду, пока создавался аккаунт.
            // Тогда удаляем пустой аккаунт и показываем понятную ошибку.
            runCatching { user.delete().await() }
            val taken = runCatching { isUsernameTaken(cleanUsername) }.getOrDefault(false)
            if (taken) error("Этот ник уже занят")
            throw error
        }
        Unit
    }.mapError()

    /**
     * Проверяет ник по открытому справочнику `usernames`.
     * Его можно читать до входа, поэтому проверка работает на экране регистрации.
     */
    private suspend fun isUsernameTaken(username: String): Boolean =
        database.reference.child("usernames").child(username).get().await().exists()

    suspend fun signOut() {
        auth.signOut()
        // История на телефоне не должна оставаться после выхода.
        clearLocalHistory()
    }

    private suspend fun ensureProfile() {
        val user = auth.currentUser ?: return
        val profileRef = database.getReference("users").child(user.uid)
        val snapshot = profileRef.get().await()
        if (!snapshot.exists()) {
            val name = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")
                ?: "Пользователь"
            profileRef.setValue(
                mapOf(
                    "uid" to user.uid,
                    "email" to user.email.orEmpty(),
                    "name" to name,
                    "username" to "",
                    "photoUrl" to "",
                    "about" to "",
                    "createdAt" to ServerValue.TIMESTAMP,
                    "privacy" to mapOf(
                        "email" to "all",
                        "about" to "all",
                        "lastSeen" to "all",
                    ),
                ),
            ).await()
        }
    }

    /**
     * Firebase отдаёт ошибки на английском и кодами.
     * Переводим самые частые в понятные фразы, остальные заменяем общим текстом.
     */
    private fun Result<Unit>.mapError(): Result<Unit> {
        val error = exceptionOrNull() ?: return this
        val message = when (error) {
            is FirebaseAuthException -> when (error.errorCode) {
                "ERROR_INVALID_EMAIL" -> "Неверный формат email"
                "ERROR_WRONG_PASSWORD",
                "ERROR_INVALID_CREDENTIAL",
                "ERROR_INVALID_LOGIN_CREDENTIALS" -> "Неверный email или пароль"
                "ERROR_EMAIL_ALREADY_IN_USE" -> "Этот email уже зарегистрирован"
                "ERROR_WEAK_PASSWORD" -> "Пароль слишком простой: нужно минимум 6 символов"
                "ERROR_USER_NOT_FOUND" -> "Пользователь с таким email не найден"
                "ERROR_NETWORK_REQUEST_FAILED" -> "Нет подключения к интернету"
                "ERROR_TOO_MANY_REQUESTS" -> "Слишком много попыток. Подождите немного"
                else -> "Ошибка Firebase: ${error.errorCode}"
            }
            else -> error.message ?: "Не удалось выполнить действие"
        }
        return Result.failure(Exception(message))
    }

    private companion object {
        val USERNAME_REGEX = Regex("^[a-z0-9_]{3,20}$")
    }
}
