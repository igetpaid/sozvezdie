package com.sozvezdie.messenger

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sozvezdie.messenger.core.data.di.AppContainer
import com.sozvezdie.messenger.feature.auth.AuthViewModel
import com.sozvezdie.messenger.feature.auth.LoginScreen
import com.sozvezdie.messenger.feature.auth.RegisterScreen
import com.sozvezdie.messenger.feature.chat.ChatScreen
import com.sozvezdie.messenger.feature.chat.ChatViewModel
import com.sozvezdie.messenger.feature.chat.GroupInfoScreen
import com.sozvezdie.messenger.feature.chat.GroupInfoViewModel
import com.sozvezdie.messenger.feature.chats.ChatListScreen
import com.sozvezdie.messenger.feature.chats.ChatListViewModel
import com.sozvezdie.messenger.feature.chats.NewChatScreen
import com.sozvezdie.messenger.feature.chats.NewChatViewModel
import com.sozvezdie.messenger.feature.profile.EditProfileScreen

import com.sozvezdie.messenger.feature.profile.ProfileScreen
import com.sozvezdie.messenger.feature.profile.ViewProfileScreen
import com.sozvezdie.messenger.feature.profile.ViewProfileViewModel
import com.sozvezdie.messenger.feature.profile.ProfileViewModel
import com.sozvezdie.messenger.feature.profile.SettingsScreen
import com.sozvezdie.messenger.feature.search.SearchScreen
import com.sozvezdie.messenger.feature.search.SearchViewModel

/**
 * Корень приложения.
 *
 * Пока пользователь не вошёл, показываем экраны входа и регистрации.
 * После входа открывается навигация с чатами. Переключение происходит
 * автоматически: Firebase сообщает об изменении состояния авторизации.
 */
@Composable
fun MessengerAppRoot(container: AppContainer) {
    val userId by container.authRepository.currentUserIdFlow.collectAsStateWithLifecycle()

    if (userId == null) {
        AuthFlow(container)
    } else {
        MainFlow(container)
    }
}

@Composable
private fun AuthFlow(container: AppContainer) {
    var showRegister by rememberSaveable { mutableStateOf(false) }
    val viewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.factory(container.authRepository),
    )

    if (showRegister) {
        RegisterScreen(
            viewModel = viewModel,
            onBackClick = { showRegister = false },
        )
    } else {
        LoginScreen(
            viewModel = viewModel,
            onRegisterClick = { showRegister = true },
        )
    }
}

@Composable
private fun MainFlow(container: AppContainer) {
    val navController = rememberNavController()

    // Нижнее меню всегда возвращает к одному из трёх главных экранов,
    // поэтому лишние страницы из истории убираем.
    val openChats = {
        navController.navigate("chats") {
            popUpTo("chats") { inclusive = true }
            launchSingleTop = true
        }
    }
    val openSearch = {
        navController.navigate("search") {
            popUpTo("chats")
            launchSingleTop = true
        }
    }
    val openProfile = {
        navController.navigate("profile") {
            popUpTo("chats")
            launchSingleTop = true
        }
    }

    // Короткое появление экрана. Долгая анимация выглядела как зависание,
    // а на её фоне успевал мелькнуть фон окна.
    val transitionDuration = 120
    val enter = fadeIn(animationSpec = tween(transitionDuration))
    val exit = fadeOut(animationSpec = tween(transitionDuration))

    NavHost(
        navController = navController,
        startDestination = "chats",
        enterTransition = { enter },
        exitTransition = { exit },
        popEnterTransition = { enter },
        popExitTransition = { exit },
    ) {
        composable("chats") {
            val viewModel: ChatListViewModel = viewModel(
                factory = ChatListViewModel.factory(
                    container.authRepository,
                    container.chatRepository,
                    container.userRepository,
                ),
            )
            ChatListScreen(
                viewModel = viewModel,
                onChatClick = { chatId -> navController.navigate("chat/$chatId") },
                onNewChatClick = { navController.navigate("new_chat") },
                onProfileClick = openProfile,
                onSearchClick = openSearch,
            )
        }

        composable("new_chat") {
            val viewModel: NewChatViewModel = viewModel(
                factory = NewChatViewModel.factory(
                    container.authRepository,
                    container.chatRepository,
                    container.userRepository,
                    container.mediaRepository,
                ),
            )
            NewChatScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onChatOpened = { chatId ->
                    // Экран выбора собеседника убираем из истории:
                    // после переписки кнопка «назад» вернёт к списку чатов.
                    navController.navigate("chat/$chatId") {
                        popUpTo("chats")
                    }
                },
            )
        }

        composable("chat/{chatId}") { entry ->
            val chatId = entry.arguments?.getString("chatId")
            if (chatId != null) {
                val viewModel: ChatViewModel = viewModel(
                    key = chatId,
                    factory = ChatViewModel.factory(
                        chatId = chatId,
                        authRepository = container.authRepository,
                        chatRepository = container.chatRepository,
                        messageRepository = container.messageRepository,
                        mediaRepository = container.mediaRepository,
                        userRepository = container.userRepository,
                        settingsStore = container.settingsStore,
                    ),
                )
                ChatScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenGroupInfo = { navController.navigate("group_info/$chatId") },
                    onViewProfile = { uid -> navController.navigate("view_profile/$uid") },
                )
            }
        }

        composable("group_info/{chatId}") { entry ->
            val chatId = entry.arguments?.getString("chatId")
            if (chatId != null) {
                val viewModel: GroupInfoViewModel = viewModel(
                    key = chatId,
                    factory = GroupInfoViewModel.factory(
                        chatId = chatId,
                        authRepository = container.authRepository,
                        chatRepository = container.chatRepository,
                        userRepository = container.userRepository,
                        mediaRepository = container.mediaRepository,
                        settingsStore = container.settingsStore,
                    ),
                )
                GroupInfoScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onLeft = {
                        navController.navigate("chats") {
                            popUpTo("chats") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
        }

        composable("search") {
            val viewModel: SearchViewModel = viewModel(
                factory = SearchViewModel.factory(
                    container.authRepository,
                    container.chatRepository,
                    container.messageRepository,
                ),
            )
            SearchScreen(
                viewModel = viewModel,
                onChatClick = { chatId -> navController.navigate("chat/$chatId") },
                onChats = openChats,
                onProfile = openProfile,
            )
        }

        composable("profile") {
            val viewModel: ProfileViewModel = viewModel(
                factory = profileFactory(container),
            )
            ProfileScreen(
                viewModel = viewModel,
                onEditProfile = { navController.navigate("edit_profile") },
                onSettings = { navController.navigate("settings") },
                onChats = openChats,
                onSearch = openSearch,
            )
        }

        composable("edit_profile") {
            val viewModel: ProfileViewModel = viewModel(
                factory = profileFactory(container),
            )
            EditProfileScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings") {
            val viewModel: ProfileViewModel = viewModel(
                factory = profileFactory(container),
            )
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable("view_profile/{uid}") { entry ->
            val uid = entry.arguments?.getString("uid").orEmpty()
            if (uid.isNotBlank()) {
                val viewModel: ViewProfileViewModel = viewModel(
                    factory = ViewProfileViewModel.factory(uid, container.userRepository),
                )
                ViewProfileScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun profileFactory(container: AppContainer) = ProfileViewModel.factory(
    authRepository = container.authRepository,
    userRepository = container.userRepository,
    mediaRepository = container.mediaRepository,
    settingsStore = container.settingsStore,
)
