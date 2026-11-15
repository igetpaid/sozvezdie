package com.sozvezdie.messenger.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** Три главных раздела приложения. */
enum class MainTab {
    CHATS,
    SEARCH,
    PROFILE,
}

/** Нижнее меню: чаты, поиск и профиль. */
@Composable
fun MessengerBottomBar(
    current: MainTab,
    onChats: () -> Unit,
    onSearch: () -> Unit,
    onProfile: () -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = current == MainTab.CHATS,
            onClick = onChats,
            icon = { Icon(Icons.Default.ChatBubble, contentDescription = null) },
            label = { Text("Чаты") },
        )
        NavigationBarItem(
            selected = current == MainTab.SEARCH,
            onClick = onSearch,
            icon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("Поиск") },
        )
        NavigationBarItem(
            selected = current == MainTab.PROFILE,
            onClick = onProfile,
            icon = { Icon(Icons.Default.Person, contentDescription = null) },
            label = { Text("Профиль") },
        )
    }
}
