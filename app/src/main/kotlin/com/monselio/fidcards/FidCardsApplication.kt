package com.monselio.fidcards

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder
import com.monselio.fidcards.data.DebugSeed
import com.monselio.fidcards.shortcuts.CardShortcuts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

class FidCardsApplication : Application(), SingletonImageLoader.Factory {

    /** Coil sans réseau : décodeurs locaux uniquement, dont SVG pour les logos. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        if (BuildConfig.DEBUG) {
            seedIfEmpty()
        }
        keepShortcutsInSync()
    }

    private fun keepShortcutsInSync() {
        applicationScope.launch {
            container.cardRepository.observeAll()
                .distinctUntilChangedBy(CardShortcuts::projection)
                .collect { cards -> CardShortcuts.update(this@FidCardsApplication, cards) }
        }
    }

    private fun seedIfEmpty() {
        applicationScope.launch {
            val repository = container.cardRepository
            if (repository.count() == 0) {
                DebugSeed.cards(System.currentTimeMillis()).forEach { repository.insert(it) }
            }
        }
    }
}
