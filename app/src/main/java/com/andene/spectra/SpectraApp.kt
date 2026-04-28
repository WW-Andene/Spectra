package com.andene.spectra

import android.app.Application
import com.andene.spectra.core.SpectraOrchestrator
import com.andene.spectra.data.codedb.IrCodeDatabase
import com.andene.spectra.data.repository.DeviceRepository
import com.andene.spectra.data.repository.MacroRepository
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

    lateinit var codeDatabase: IrCodeDatabase
        private set

    lateinit var macroRepository: MacroRepository
        private set

    // App-scoped coroutine scope for one-shot startup work that should
    // outlive any single Activity/ViewModel.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        repository = DeviceRepository(this)
        codeDatabase = IrCodeDatabase(this)
        macroRepository = MacroRepository(this)
        orchestrator = SpectraOrchestrator(this)

        // Seed the matcher and IR registry with previously saved devices so
        // re-identification works on the first scan after a fresh launch.
        appScope.launch {
            val saved = repository.loadAll()
            if (saved.isNotEmpty()) orchestrator.loadKnownDevices(saved)
        }

        // Prime the IR code database off the main thread so the brand
        // picker on Learn doesn't stall the first time the user opens it.
        appScope.launch { codeDatabase.preload() }
    }

    override fun onTerminate() {
        orchestrator.release()
        appScope.cancel()
        super.onTerminate()
    }
}
