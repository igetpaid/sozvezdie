package com.sozvezdie.messenger.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

// Набор приятных цветов для аватаров. Цвет выбирается по имени,
// чтобы у одного и того же человека он никогда не менялся.
private val AvatarColors = listOf(
    Color(0xFF4F46E5),
    Color(0xFF0EA5E9),
    Color(0xFF10B981),
    Color(0xFFF59E0B),
    Color(0xFFEF4444),
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
)

/**
 * Аватар пользователя или группы.
 *
 * Если есть ссылка на фотографию, показываем её. Иначе рисуем
 * кружок с первой буквой имени, так список выглядит живым
 * даже до того, как человек загрузил фото.
 */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    photoUrl: String = "",
    online: Boolean? = null,
    colorIndex: Int? = null,
) {
    // Если цвет задан снаружи, берём его. Иначе выбираем по имени,
    // как и раньше, чтобы у человека цвет не менялся.
    val color = if (colorIndex != null) {
        AvatarColors[Math.floorMod(colorIndex, AvatarColors.size)]
    } else {
        AvatarColors[Math.floorMod(name.hashCode(), AvatarColors.size)]
    }
    val secondColor = color.copy(alpha = 0.72f)

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(color, secondColor))),
            contentAlignment = Alignment.Center,
        ) {
            if (photoUrl.isNotBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Фото профиля",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape),
                )
            } else {
                Text(
                    text = name.trim().firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontSize = (size.value * 0.4f).sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Зелёная точка в углу показывает, что человек сейчас в сети.
        if (online != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.28f)
                    .clip(CircleShape)
                    .background(
                        if (online) Color(0xFF22C55E) else Color(0xFF9CA3AF),
                    )
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}
