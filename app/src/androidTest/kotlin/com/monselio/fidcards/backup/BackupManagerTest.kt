package com.monselio.fidcards.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monselio.fidcards.data.BarcodeFormat
import com.monselio.fidcards.data.Card
import com.monselio.fidcards.data.CardRepository
import com.monselio.fidcards.data.FidCardsDatabase
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Aller-retour sans perte (docs/PROJET.md §13 phase 5). */
@RunWith(AndroidJUnit4::class)
class BackupManagerTest {

    private lateinit var context: Context
    private lateinit var db: FidCardsDatabase
    private lateinit var repository: CardRepository
    private lateinit var manager: BackupManager
    private lateinit var zipFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FidCardsDatabase::class.java).build()
        repository = CardRepository(db.cardDao())
        manager = BackupManager(context, repository)
        zipFile = File(context.cacheDir, "test-backup.zip")
    }

    @After
    fun tearDown() {
        db.close()
        zipFile.delete()
        manager.imagesDir.listFiles()?.forEach { it.delete() }
    }

    private fun sampleCards(logoPath: String?) = listOf(
        Card(
            name = "Carrefour",
            barcodeValue = "4006381333931",
            barcodeFormat = BarcodeFormat.EAN_13,
            brandId = "carrefour",
            createdAt = 1_000,
            lastUsedAt = 5_000,
            useCount = 3,
            score = 2.5,
        ),
        Card(
            name = "Sephora",
            barcodeValue = "SEPH-QR-123",
            displayValue = "0042",
            barcodeFormat = BarcodeFormat.QR_CODE,
            colorArgb = 0xFFC62828.toInt(),
            logoPath = logoPath,
            createdAt = 2_000,
        ),
    )

    @Test
    fun aller_retour_sans_perte() = runBlocking {
        // Une « image » référencée, pour vérifier le transport des fichiers.
        val logo = File(manager.imagesDir, "logo-test.png").apply {
            writeBytes(byteArrayOf(0x50, 0x4E, 0x47, 42))
        }
        sampleCards(logo.absolutePath).forEach { repository.insert(it) }

        val export = manager.export(Uri.fromFile(zipFile))
        assertEquals(BackupResult.ExportOk(2), export)
        assertTrue("archive vide", zipFile.length() > 0)

        // Téléphone neuf : base vidée, images supprimées.
        repository.getAll().forEach { repository.delete(it) }
        manager.imagesDir.listFiles()?.forEach { it.delete() }

        val import = manager.import(Uri.fromFile(zipFile), ImportMode.MERGE)
        assertEquals(BackupResult.ImportOk(imported = 2, skipped = 0), import)

        val restored = repository.getAll().sortedBy { it.createdAt }
        assertEquals(2, restored.size)

        val carrefour = restored[0]
        assertEquals("Carrefour", carrefour.name)
        assertEquals("4006381333931", carrefour.barcodeValue)
        assertEquals(BarcodeFormat.EAN_13, carrefour.barcodeFormat)
        assertEquals("carrefour", carrefour.brandId)
        assertEquals(5_000L, carrefour.lastUsedAt)
        assertEquals(3, carrefour.useCount)
        assertEquals(2.5, carrefour.score, 1e-9)

        val sephora = restored[1]
        assertEquals("0042", sephora.displayValue)
        assertEquals(0xFFC62828.toInt(), sephora.colorArgb)
        assertNotNull("logo non restauré", sephora.logoPath)
        val restoredLogo = File(sephora.logoPath!!)
        assertTrue("fichier logo absent", restoredLogo.isFile)
        assertEquals(4, restoredLogo.length())
    }

    @Test
    fun le_reimport_deduplique() = runBlocking {
        sampleCards(null).forEach { repository.insert(it) }
        manager.export(Uri.fromFile(zipFile))

        // Réimporter sur la même base : tout doit être ignoré.
        val again = manager.import(Uri.fromFile(zipFile), ImportMode.MERGE)
        assertEquals(BackupResult.ImportOk(imported = 0, skipped = 2), again)
        assertEquals(2, repository.count())
    }

    @Test
    fun remplacer_ecrase_l_existant() = runBlocking {
        sampleCards(null).forEach { repository.insert(it) }
        manager.export(Uri.fromFile(zipFile))

        repository.insert(
            Card(name = "Intrus", barcodeValue = "999", barcodeFormat = BarcodeFormat.CODE_128, createdAt = 9_000),
        )

        val result = manager.import(Uri.fromFile(zipFile), ImportMode.REPLACE)
        assertEquals(BackupResult.ImportOk(imported = 2, skipped = 0), result)
        val names = repository.getAll().map { it.name }.sorted()
        assertEquals(listOf("Carrefour", "Sephora"), names)
    }

    @Test
    fun version_inconnue_refusee() = runBlocking {
        java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("cards.json"))
            zip.write("""{"schemaVersion": 99, "cards": []}""".toByteArray())
            zip.closeEntry()
        }
        val result = manager.import(Uri.fromFile(zipFile), ImportMode.MERGE)
        assertEquals(BackupResult.UnknownVersion, result)
    }
}
