package com.monselio.fidcards

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder
import com.monselio.fidcards.data.DebugSeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
