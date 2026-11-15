package com.sozvezdie.messenger.feature.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sozvezdie.messenger.core.data.local.SettingsStore
import com.sozvezdie.messenger.core.data.repository.AuthRepository
import com.sozvezdie.messenger.core.data.repository.MediaRepository
import com.sozvezdie.messenger.core.data.repository.UserRepository
import com.sozvezdie.messenger.core.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Профиль пользователя: редактирование и настройки.
 *
 * Данные профиля читаются из Firebase, а тема хранится только
 * на телефоне, потому что это личная настройка каждого устройства.
 */
class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val mediaRepository: MediaRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val myUid: String = authRepository.currentUserId.orEmpty()

    val profile: StateFlow<User?> = userRepository.observeProfile(myUid)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val theme: StateFlow<String> = settingsStore.themeFlow

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Сохраняет профиль. Если выбрано новое фото, сначала загружаем его,
     * и только потом записываем остальные поля.
     */
    fun saveProfile(
        name: String,
        username: String,
        about: String,
        photoUri: Uri?,
        onSaved: () -> Unit,
    ) {
        if (_saving.value) return
        viewModelScope.launch {
            _saving.value = true
            _error.value = null

            var photoUrl = profile.value?.photoUrl.orEmpty()
            if (photoUri != null) {
                val uploaded = mediaRepository.uploadAvatar(myUid, photoUri)
                if (uploaded.isFailure) {
                    _saving.value = false
                    _error.value = uploaded.exceptionOrNull()?.message
                        ?: "Не удалось загрузить фото"
                    return@launch
                }
                photoUrl = uploaded.getOrNull().orEmpty()
            }

            userRepository.updateProfile(
                uid = myUid,
                name = name,
                username = username,
                about = about,
                photoUrl = photoUrl,
            )
                .onSuccess {
                    _saving.value = false
                    onSaved()
                }
                .onFailure { error ->
                    _saving.value = false
                    _error.value = error.message ?: "Не удалось сохранить профиль"
                }
        }
    }

    fun setTheme(theme: String) {
        settingsStore.setTheme(theme)
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    fun clearError() {
        _error.value = null
    }

    companion object {
        fun factory(
            authRepository: AuthRepository,
            userRepository: UserRepository,
            mediaRepository: MediaRepository,
            settingsStore: SettingsStore,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProfileViewModel(
                    authRepository = authRepository,
                    userRepository = userRepository,
                    mediaRepository = mediaRepository,
                    settingsStore = settingsStore,
                )
            }
        }
    }
}
