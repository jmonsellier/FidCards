package com.monselio.fidcards.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.monselio.fidcards.MainActivity
import com.monselio.fidcards.R
import com.monselio.fidcards.data.Card
import com.monselio.fidcards.data.RecencyScore

/**
 * Raccourcis d'écran d'accueil (appui long sur l'icône) : les cartes les plus
 * utilisées ouvrent directement le plein écran — le chemin critique raccourci
 * d'un écran (docs/PROJET.md §1 et §13 phase 7).
 */
object CardShortcuts {

    const val EXTRA_CARD_ID = "com.monselio.fidcards.extra.CARD_ID"
    private const val MAX_SHORTCUTS = 3

    /** Projection stable pour ne pousser une mise à jour que si le podium change. */
    fun projection(cards: List<Card>): List<Pair<Long, String>> = top(cards).map { it.id to it.name }

    fun update(context: Context, cards: List<Card>) {
        val shortcuts = top(cards).map { card ->
            ShortcutInfoCompat.Builder(context, "card-${card.id}")
                .setShortLabel(card.name.take(25))
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(
                    Intent(context, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .putExtra(EXTRA_CARD_ID, card.id),
                )
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    private fun top(cards: List<Card>): List<Card> {
        val now = System.currentTimeMillis()
        return cards
            .filter { it.lastUsedAt != null && it.name.isNotBlank() }
            .sortedByDescending { RecencyScore.effective(it, now) }
            .take(MAX_SHORTCUTS)
    }
}
