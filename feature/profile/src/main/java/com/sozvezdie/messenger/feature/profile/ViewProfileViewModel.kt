package com.sozvezdie.messenger.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sozvezdie.messenger.core.data.repository.UserRepository
import com.sozvezdie.messenger.core.model.User
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ViewProfileViewModel(
    private val userRepository: UserRepository,
    private val uid: String,
) : ViewModel() {
    val profile: StateFlow<User?> = userRepository.observeProfile(uid)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    companion object {
        fun factory(uid: String, userRepository: UserRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ViewProfileViewModel(userRepository, uid) }
            }
    }
}