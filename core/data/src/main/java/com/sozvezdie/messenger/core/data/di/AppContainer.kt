package com.sozvezdie.messenger.core.data.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.sozvezdie.messenger.core.data.FirebaseConfig
import com.sozvezdie.messenger.core.data.local.AppDatabase
import com.sozvezdie.messenger.core.data.local.SettingsStore
import com.sozvezdie.messenger.core.data.remote.SupabaseStorage
import com.sozvezdie.messenger.core.data.repository.AuthRepository
import com.sozvezdie.messenger.core.data.repository.ChatRepository
import com.sozvezdie.messenger.core.data.repository.MediaRepository
import com.sozvezdie.messenger.core.data.repository.MessageRepository
import com.sozvezdie.messenger.core.data.repository.PresenceManager
import com.sozvezdie.messenger.core.data.repository.UserRepository

/**
 * Ручное внедрение зависимостей.
 *
 * Все Firebase-объекты создаются один раз здесь и передаются экранам
 * через конструкторы ViewModel. Библиотека Hilt для учебного проекта
 * избыточна: зависимостей немного, и так проще понять, что откуда берётся.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val auth = FirebaseAuth.getInstance()

    // setPersistenceEnabled включает кеш Firebase: данные читаются и без сети.
    private val database = FirebaseDatabase.getInstance(FirebaseConfig.DATABASE_URL).apply {
        setPersistenceEnabled(true)
    }

    private val localDatabase = AppDatabase.build(appContext)

    // Настройки телефона: тема, обои и черновики.
    val settingsStore = SettingsStore(appContext)

    val authRepository = AuthRepository(
        auth = auth,
        database = database,
        // При выходе чистим Room и локальные настройки, чтобы следующий
        // пользователь не увидел чужую историю.
        clearLocalHistory = {
            localDatabase.messageDao().clear()
            localDatabase.chatDao().clear()
            settingsStore.clearLocalSettings()
        },
    )

    val userRepository = UserRepository(database, auth)
    val chatRepository = ChatRepository(database, localDatabase.chatDao(), localDatabase.messageDao())
    val messageRepository = MessageRepository(database, localDatabase.messageDao())

    // Медиа лежит в Supabase Storage, а в Firebase остаётся только ссылка.
    private val supabaseStorage = SupabaseStorage(appContext)
    val mediaRepository = MediaRepository(appContext, supabaseStorage)

    val presenceManager = PresenceManager(auth, database)

    init {
        presenceManager.start()
    }
}
