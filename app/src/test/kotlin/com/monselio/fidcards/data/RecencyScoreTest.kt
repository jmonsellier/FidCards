package com.monselio.fidcards.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecencyScoreTest {

    private fun card(score: Double, lastUsedAt: Long?) = Card(
        id = 1,
        name = "Test",
        barcodeValue = "123",
        barcodeFormat = BarcodeFormat.EAN_13,
        createdAt = 0,
        lastUsedAt = lastUsedAt,
        score = score,
    )

    @Test
    fun `le score est divise par deux apres une demi-vie`() {
        assertEquals(2.0, RecencyScore.decay(4.0, RecencyScore.HALF_LIFE_MS), 1e-9)
    }

    @Test
    fun `le score est intact a delta zero`() {
        assertEquals(4.0, RecencyScore.decay(4.0, 0), 1e-9)
    }

    @Test
    fun `deux demi-vies divisent par quatre`() {
        assertEquals(1.0, RecencyScore.decay(4.0, 2 * RecencyScore.HALF_LIFE_MS), 1e-9)
    }

    @Test
    fun `le score effectif d'une carte jamais utilisee est nul`() {
        assertEquals(0.0, RecencyScore.effective(card(5.0, null), now = 1_000), 1e-9)
    }

    @Test
    fun `une carte tres utilisee jadis passe sous une carte utilisee recemment`() {
        val now = 100L * RecencyScore.HALF_LIFE_MS
        // 10 usages il y a un an (12 demi-vies) contre 2 usages aujourd'hui.
        val ancienne = card(10.0, now - 12 * RecencyScore.HALF_LIFE_MS)
        val recente = card(2.0, now)
        val scoreAncienne = RecencyScore.effective(ancienne, now)
        val scoreRecente = RecencyScore.effective(recente, now)
        check(scoreRecente > scoreAncienne) {
            "attendu récente ($scoreRecente) > ancienne ($scoreAncienne)"
        }
    }
}
