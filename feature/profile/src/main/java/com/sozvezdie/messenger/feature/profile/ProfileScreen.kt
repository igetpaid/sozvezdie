package com.sozvezdie.messenger.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sozvezdie.messenger.core.model.User
import com.sozvezdie.messenger.core.ui.components.Avatar
import com.sozvezdie.messenger.core.ui.components.MainTab
import com.sozvezdie.messenger.core.ui.components.MessengerBottomBar

/**
 * Профиль: фото, имя, ник и контакты.
 *
 * Отсюда можно перейти к редактированию, приватности и настройкам.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onEditProfile: () -> Unit,
    onSettings: () -> Unit,
    onChats: () -> Unit,
    onSearch: () -> Unit,
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Профиль", fontWeight = FontWeight.SemiBold) },
            )
        },
        bottomBar = {
            MessengerBottomBar(
                current = MainTab.PROFILE,
                onChats = onChats,
                onSearch = onSearch,
                onProfile = {},
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Avatar(
                name = profile?.name ?: "Профиль",
                photoUrl = profile?.photoUrl.orEmpty(),
                size = 112.dp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = profile?.name ?: "Загрузка…",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (!profile?.username.isNullOrBlank()) {
                Text(
                    text = profile?.usernameLabel.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (!profile?.about.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = profile?.about.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))

            ProfileCard(profile = profile)

            Spacer(Modifier.height(20.dp))

            MenuRow(
                title = "Редактировать профиль",
                icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = onEditProfile,
            )
            MenuRow(
                title = "Настройки",
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                onClick = onSettings,
            )
            MenuRow(
                title = "Выйти из аккаунта",
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                titleColor = MaterialTheme.colorScheme.error,
                onClick = viewModel::signOut,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Карточка с email и ником. */
@Composable
private fun ProfileCard(profile: User?) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Контакты",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            InfoLine(label = "Email", value = profile?.email.orEmpty().ifBlank { "—" })
            Spacer(Modifier.height(8.dp))
            InfoLine(
                label = "Ник",
                value = profile?.usernameLabel.orEmpty().ifBlank { "не задан" },
            )
            Spacer(Modifier.height(8.dp))
            InfoLine(
                label = "О себе",
                value = profile?.about.orEmpty().ifBlank { "не заполнено" },
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Строка меню с иконкой и стрелкой. */
@Composable
private fun MenuRow(
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            icon()
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
