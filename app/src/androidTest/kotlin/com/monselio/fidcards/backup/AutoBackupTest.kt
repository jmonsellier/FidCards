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
import com.monselio.fidcards.data.ImageStore
import com.monselio.fidcards.settings.SettingsRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
class AutoBackupTest {

    private lateinit var context: Context
    private lateinit var db: FidCardsDatabase
    private lateinit var repository: CardRepository
    private lateinit var settings: SettingsRepository
    private lateinit var autoBackup: AutoBackup
    private lateinit var manager: BackupManager
    private lateinit var target: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FidCardsDatabase::class.java).build()
        repository = CardRepository(db.cardDao())
        settings = SettingsRepository(context)
        manager = BackupManager(context, repository, ImageStore(context))
        autoBackup = AutoBackup(context, settings, manager, repository, debounceMs = 150)
        target = File(context.cacheDir, "auto-backup-test.zip")
    }

    @After
    fun tearDown() {
        runBlocking { autoBackup.disable() }
        db.close()
        target.delete()
    }

    private fun card(name: String, value: String) =
        Card(name = name, barcodeValue = value, barcodeFormat = BarcodeFormat.CODE_128, createdAt = 1)

    @Test
    fun activation_export_immediat_puis_reecriture_a_chaque_modification() = runBlocking {
        repository.insert(card("Carrefour", "111"))

        // Activation : export immédiat et uri mémorisée.
        val result = autoBackup.enable(Uri.fromFile(target))
        assertEquals(BackupResult.ExportOk(1), result)
        assertTrue(target.length() > 0)
        assertEquals(Uri.fromFile(target).toString(), settings.autoBackupUri.first())

        // Une modification ultérieure réécrit l'archive, sous différé.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        autoBackup.start(scope)
        delay(200) // laisse le collecteur s'abonner (la 1re émission est ignorée)
        repository.insert(card("Picard", "222"))
        delay(1_500)
        scope.cancel()

        // L'archive relue contient bien les deux cartes.
        val restore = Room.inMemoryDatabaseBuilder(context, FidCardsDatabase::class.java).build()
        try {
            val restoreRepo = CardRepository(restore.cardDao())
            val imported = BackupManager(context, restoreRepo, ImageStore(context))
                .import(Uri.fromFile(target), ImportMode.MERGE)
            assertEquals(BackupResult.ImportOk(imported = 2, skipped = 0), imported)
        } finally {
            restore.close()
        }

        // Désactivation : l'uri disparaît.
        autoBackup.disable()
        assertNull(settings.autoBackupUri.first())
    }
}
