package com.monselio.fidcards

import android.app.Application
import com.monselio.fidcards.data.DebugSeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FidCardsApplication : Application() {

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
