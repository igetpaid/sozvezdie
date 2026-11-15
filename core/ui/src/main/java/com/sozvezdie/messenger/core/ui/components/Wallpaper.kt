package com.sozvezdie.messenger.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File

/** Один вариант обоев: номер и пара цветов градиента. */
data class WallpaperOption(
    val id: String,
    val colors: List<Color>,
)

// Шесть спокойных градиентов. Они работают и в светлой, и в тёмной теме.
val wallpaperOptions = listOf(
    WallpaperOption("gradient:0", listOf(Color(0xFFEEF2FF), Color(0xFFE0E7FF))),
    WallpaperOption("gradient:1", listOf(Color(0xFFFDF2F8), Color(0xFFFCE7F3))),
    WallpaperOption("gradient:2", listOf(Color(0xFFECFDF5), Color(0xFFD1FAE5))),
    WallpaperOption("gradient:3", listOf(Color(0xFFFFFBEB), Color(0xFFFEF3C7))),
    WallpaperOption("gradient:4", listOf(Color(0xFFF5F3FF), Color(0xFFEDE9FE))),
    WallpaperOption("gradient:5", listOf(Color(0xFFF0F9FF), Color(0xFFE0F2FE))),
)

/**
 * Фон переписки.
 *
 * Пустая строка это обычный фон темы. Строка «gradient:N» означает один из
 * готовых градиентов. Любая другая строка это путь к своей фотографии.
 */
@Composable
fun WallpaperBackground(
    spec: String,
    modifier: Modifier = Modifier,
) {
    if (spec.isBlank()) return

    if (spec.startsWith("gradient:")) {
        val index = spec.removePrefix("gradient:").toIntOrNull() ?: 0
        val option = wallpaperOptions.getOrElse(index) { wallpaperOptions.first() }
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Brush.linearGradient(option.colors)),
        )
        return
    }

    val file = File(spec.removePrefix("file:"))
    if (file.exists()) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
    }
}
