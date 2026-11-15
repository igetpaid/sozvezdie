package com.sozvezdie.messenger

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sozvezdie.messenger.core.ui.theme.MessengerTheme

/**
 * Единственная Activity. Весь интерфейс написан на Compose,
 * поэтому отдельные XML-экраны не нужны.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MessengerApp).container

        // Когда приложение сворачивают, сразу показываем «был(а) недавно»,
        // а при возвращении снова ставим «в сети».
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.presenceManager.onAppForeground()
            }

            override fun onStop(owner: LifecycleOwner) {
                container.presenceManager.onAppBackground()
            }
        })

        setContent {
            val theme by container.settingsStore.themeFlow.collectAsStateWithLifecycle()

            // Пользователь выбирает тему в настройках: «как в системе», светлая или тёмная.
            val darkTheme = when (theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            MessengerTheme(darkTheme = darkTheme) {
                val view = LocalView.current
                val backgroundColor = MaterialTheme.colorScheme.background

                // Фон окна должен совпадать с выбранной темой, а не с системной.
                // Иначе при ручной тёмной теме между экранами мелькает светлый фон.
                SideEffect {
                    window.setBackgroundDrawable(ColorDrawable(backgroundColor.toArgb()))
                    if (!view.isInEditMode) {
                        val controller = WindowCompat.getInsetsController(window, view)
                        controller.isAppearanceLightStatusBars = !darkTheme
                        controller.isAppearanceLightNavigationBars = !darkTheme
                    }
                }

                MessengerAppRoot(container)
            }
        }
    }
}
