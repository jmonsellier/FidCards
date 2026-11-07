package com.monselio.fidcards.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class CardRepository(
    private val database: FidCardsDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val dao: CardDao = database.cardDao()

    fun observeAll(): Flow<List<Card>> = dao.observeAll()

    fun observeById(id: Long): Flow<Card?> = dao.observeById(id)

    suspend fun getById(id: Long): Card? = dao.getById(id)

    suspend fun getAll(): List<Card> = dao.getAll()

    suspend fun count(): Int = dao.count()

    suspend fun insert(card: Card): Long = dao.insert(card)

    suspend fun insertAll(cards: List<Card>) {
        if (cards.isNotEmpty()) dao.insertAll(cards)
    }

    suspend fun update(card: Card) = dao.update(card)

    suspend fun delete(card: Card) = dao.delete(card)

    suspend fun deleteAll() = dao.deleteAll()

    /**
     * Exécute [block] en une transaction unique : tout ou rien. Indispensable à
     * l'import « remplacer », qui vide la bibliothèque avant de la reconstruire
     * — une interruption en cours de route ne doit rien laisser derrière elle.
     */
    suspend fun <T> inTransaction(block: suspend () -> T): T = database.withTransaction(block)

    /**
     * Enregistre une ouverture en plein écran : score décayé puis incrémenté,
     * `lastUsedAt` et `useCount` mis à jour — sous anti-rebond d'une minute
     * (consulter le verso puis revenir ne compte pas deux fois).
     */
    suspend fun recordUsage(cardId: Long) {
        val card = dao.getById(cardId) ?: return
        val now = clock()
        val last = card.lastUsedAt
        if (last != null && now - last < RecencyScore.DEBOUNCE_MS) return
        val decayed = if (last != null) RecencyScore.decay(card.score, (now - last).coerceAtLeast(0)) else 0.0
        dao.update(
            card.copy(
                score = decayed + 1,
                lastUsedAt = now,
                useCount = card.useCount + 1,
            ),
        )
    }
}
