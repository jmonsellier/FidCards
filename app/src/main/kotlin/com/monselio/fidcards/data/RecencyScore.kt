package com.monselio.fidcards.data

import kotlin.math.pow

/**
 * Score de récence pondérée : décroissance exponentielle, demi-vie 30 jours,
 * appliquée paresseusement à chaque ouverture plein écran (docs/PROJET.md §4.1).
 *
 *     score = score × 0,5 ^ (Δjours / 30) + 1
 */
object RecencyScore {

    const val HALF_LIFE_MS: Long = 30L * 24 * 60 * 60 * 1000

    /** Rouvrir la même carte dans la minute ne compte pas comme deux usages. */
    const val DEBOUNCE_MS: Long = 60_000

    fun decay(score: Double, elapsedMs: Long): Double =
        score * 0.5.pow(elapsedMs.toDouble() / HALF_LIFE_MS)

    /** Score « vivant » d'une carte à l'instant [now], pour le tri par défaut. */
    fun effective(card: Card, now: Long): Double {
        val last = card.lastUsedAt ?: return 0.0
        return decay(card.score, (now - last).coerceAtLeast(0))
    }
}
