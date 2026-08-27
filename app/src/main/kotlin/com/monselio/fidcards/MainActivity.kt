package com.monselio.fidcards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.monselio.fidcards.ui.FidCardsApp
import com.monselio.fidcards.ui.theme.FidCardsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FidCardsTheme {
                FidCardsApp()
            }
        }
    }
}
