package com.andene.spectra

import android.app.Application
import com.andene.spectra.core.SpectraOrchestrator
import com.andene.spectra.data.repository.DeviceRepository

class SpectraApp : Application() {

    lateinit var orchestrator: SpectraOrchestrator
        private set

    lateinit var repository: DeviceRepository
        private set

    override fun onCreate() {
        super.onCreate()
        orchestrator = SpectraOrchestrator(this)
        repository = DeviceRepository(this)
    }

    override fun onTerminate() {
        orchestrator.release()
        super.onTerminate()
    }
}
