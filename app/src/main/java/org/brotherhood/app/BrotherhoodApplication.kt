package org.brotherhood.app

import android.app.Application
import org.brotherhood.app.crypto.CryptoEngine
import org.brotherhood.app.data.BrotherhoodRepository
import org.brotherhood.app.storage.SecureStateStore
import org.brotherhood.app.transport.LanTransport

class BrotherhoodApplication : Application() {
    lateinit var repository: BrotherhoodRepository
        private set
    lateinit var lanTransport: LanTransport
        private set

    override fun onCreate() {
        super.onCreate()
        val crypto = CryptoEngine()
        repository = BrotherhoodRepository(SecureStateStore(this), crypto)
        lanTransport = LanTransport(this, repository)
    }

    override fun onTerminate() {
        lanTransport.close()
        super.onTerminate()
    }
}
