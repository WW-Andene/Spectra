package com.andene.spectra

import android.app.Application
import com.andene.spectra.core.SpectraOrchestrator
import com.andene.spectra.data.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SpectraApp : Application() {

    lateinit var orchestrator: SpectraOrchestrator
        private set

    lateinit var repository: DeviceRepository
        private set

    // App-scoped coroutine scope for one-shot startup work that should
    // outlive any single Activity/ViewModel.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        repository = DeviceRepository(this)
        orchestrator = SpectraOrchestrator(this)

        // Seed the matcher and IR registry with previously saved devices so
        // re-identification works on the first scan after a fresh launch.
        appScope.launch {
            val saved = repository.loadAll()
            if (saved.isNotEmpty()) orchestrator.loadKnownDevices(saved)
        }
    }

    override fun onTerminate() {
        orchestrator.release()
        appScope.cancel()
        super.onTerminate()
    }
}
