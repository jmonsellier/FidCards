package com.monselio.fidcards.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardDaoTest {

    private lateinit var db: FidCardsDatabase
    private lateinit var dao: CardDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FidCardsDatabase::class.java,
        ).build()
        dao = db.cardDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun card(
        name: String = "Carrefour",
        value: String = "3270190000000",
        format: BarcodeFormat = BarcodeFormat.EAN_13,
    ) = Card(name = name, barcodeValue = value, barcodeFormat = format, createdAt = 42)

    @Test
    fun insertion_et_relecture() = runBlocking {
        val id = dao.insert(card())
        val lu = dao.getById(id)!!
        assertEquals("Carrefour", lu.name)
        assertEquals("3270190000000", lu.barcodeValue)
        assertEquals(BarcodeFormat.EAN_13, lu.barcodeFormat)
        assertNull(lu.displayValue)
        assertEquals(0, lu.useCount)
        assertEquals(0.0, lu.score, 0.0)
    }

    @Test
    fun le_convertisseur_couvre_tous_les_formats() = runBlocking {
        for (format in BarcodeFormat.entries) {
            val id = dao.insert(card(name = format.name, value = "123", format = format))
            assertEquals(format, dao.getById(id)!!.barcodeFormat)
        }
        assertEquals(BarcodeFormat.entries.size, dao.count())
    }

    @Test
    fun mise_a_jour() = runBlocking {
        val id = dao.insert(card())
        val avant = dao.getById(id)!!
        dao.update(avant.copy(name = "Monoprix", score = 3.5, useCount = 2))
        val apres = dao.getById(id)!!
        assertEquals("Monoprix", apres.name)
        assertEquals(3.5, apres.score, 0.0)
        assertEquals(2, apres.useCount)
    }

    @Test
    fun suppression() = runBlocking {
        val id = dao.insert(card())
        dao.delete(dao.getById(id)!!)
        assertNull(dao.getById(id))
        assertEquals(0, dao.count())
    }

    @Test
    fun le_flow_reflete_les_insertions() = runBlocking {
        dao.insert(card(name = "A"))
        dao.insert(card(name = "B", value = "456"))
        val cartes = dao.observeAll().first()
        assertEquals(listOf("A", "B"), cartes.map { it.name }.sorted())
    }

    @Test
    fun record_usage_applique_le_score_et_l_anti_rebond() = runBlocking {
        var now = 0L
        val repo = CardRepository(db, clock = { now })

        val id = dao.insert(card())

        now = 1_000
        repo.recordUsage(id)
        var lu = dao.getById(id)!!
        assertEquals(1.0, lu.score, 1e-9)
        assertEquals(1, lu.useCount)
        assertEquals(1_000L, lu.lastUsedAt)

        // Réouverture dans la minute : ignorée (verso puis retour).
        now = 30_000
        repo.recordUsage(id)
        lu = dao.getById(id)!!
        assertEquals(1, lu.useCount)
        assertEquals(1_000L, lu.lastUsedAt)

        // Une demi-vie plus tard : 1.0 decayé en 0.5, +1.
        now = 1_000 + RecencyScore.HALF_LIFE_MS
        repo.recordUsage(id)
        lu = dao.getById(id)!!
        assertEquals(1.5, lu.score, 1e-9)
        assertEquals(2, lu.useCount)
        assertTrue(lu.lastUsedAt == now)
    }
}
