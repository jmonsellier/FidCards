package com.monselio.fidcards

import android.content.Context
import com.monselio.fidcards.backup.AutoBackup
import com.monselio.fidcards.backup.BackupManager
import com.monselio.fidcards.brands.BrandCatalog
import com.monselio.fidcards.data.CardRepository
import com.monselio.fidcards.data.FidCardsDatabase
import com.monselio.fidcards.data.ImageStore
import com.monselio.fidcards.settings.SettingsRepository

/**
 * Injection manuelle : un conteneur unique, aucune abstraction spéculative
 * (docs/PROJET.md §11). Les dépendances s'ajoutent phase par phase.
 */
class AppContainer(private val context: Context) {

    private val database: FidCardsDatabase by lazy { FidCardsDatabase.build(context) }

    val cardRepository: CardRepository by lazy { CardRepository(database.cardDao()) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context) }

    val brandCatalog: BrandCatalog by lazy { BrandCatalog(context) }

    val imageStore: ImageStore by lazy { ImageStore(context) }

    val backupManager: BackupManager by lazy { BackupManager(context, cardRepository, imageStore) }

    val autoBackup: AutoBackup by lazy {
        AutoBackup(context, settingsRepository, backupManager, cardRepository)
    }
}
