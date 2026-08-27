package com.monselio.fidcards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monselio.fidcards.settings.ThemeMode
import com.monselio.fidcards.shortcuts.CardShortcuts
import com.monselio.fidcards.ui.FidCardsApp
import com.monselio.fidcards.ui.theme.FidCardsTheme

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
            FidCardsTheme(darkTheme = darkTheme) {
                FidCardsApp(startCardId = shortcutCardId)
            }
        }
    }
}
