package com.monselio.fidcards

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monselio.fidcards.settings.ThemeMode
import com.monselio.fidcards.shortcuts.CardShortcuts
import com.monselio.fidcards.ui.FidCardsApp
import com.monselio.fidcards.ui.theme.FidCardsTheme

/** Voiles de la barre de navigation 3 boutons, mêmes valeurs que ceux d'androidx. */
private val NAV_LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
private val NAV_DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = (application as FidCardsApplication).container.settingsRepository
        // Lancement depuis un raccourci d'écran d'accueil : plein écran direct.
        val shortcutCardId = intent
            ?.getLongExtra(CardShortcuts.EXTRA_CARD_ID, -1L)
            ?.takeIf { it > 0 }
        setContent {
            val themeMode by settings.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Les barres système doivent suivre le thème de l'app, pas celui du
            // système : sans quoi, app en sombre sur système clair, la barre de
            // navigation 3 boutons reste blanche (voile et icônes clairs).
            DisposableEffect(darkTheme) {
                val transparent = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(
                    statusBarStyle = transparent,
                    navigationBarStyle = SystemBarStyle.auto(NAV_LIGHT_SCRIM, NAV_DARK_SCRIM) { darkTheme },
                )
                onDispose {}
            }
            FidCardsTheme(darkTheme = darkTheme) {
                FidCardsApp(startCardId = shortcutCardId)
            }
        }
    }
}
