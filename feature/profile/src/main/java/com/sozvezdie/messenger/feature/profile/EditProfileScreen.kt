package com.sozvezdie.messenger.feature.profile

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sozvezdie.messenger.core.ui.components.Avatar
import com.sozvezdie.messenger.feature.media.rememberPhotoPicker

/**
 * Редактирование имени, ника, описания и фотографии.
 *
 * Ник должен быть свободен: это проверяет репозиторий перед сохранением.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var filled by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    val pickPhoto = rememberPhotoPicker { photo = it }

    LaunchedEffect(profile) {
        val current = profile ?: return@LaunchedEffect
        if (!filled) {
            name = current.name
            username = current.username
            about = current.about
            filled = true
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("Редактировать профиль", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier.clickable(onClick = pickPhoto),
                contentAlignment = Alignment.BottomEnd,
            ) {
                if (photo != null) {
                    AsyncImage(
                        model = photo,
                        contentDescription = "Новое фото профиля",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .height(110.dp)
                            .clip(RoundedCornerShape(55.dp)),
                    )
                } else {
                    Avatar(
                        name = name.ifBlank { "Профиль" },
                        photoUrl = profile?.photoUrl.orEmpty(),
                        size = 110.dp,
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = pickPhoto),
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Сменить фото",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Text(
                text = "Нажмите на фото, чтобы изменить",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Имя") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Ник") },
                placeholder = { Text("nickname") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = about,
                onValueChange = { about = it.take(120) },
                label = { Text("О себе") },
                placeholder = { Text("Пара слов о вас") },
                supportingText = { Text("${about.length}/120") },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .height(22.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Button(
                    onClick = {
                        viewModel.saveProfile(name, username, about, photo, onBack)
                    },
                    enabled = !saving && name.trim().length >= 2,
                ) {
                    Text("Сохранить")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
