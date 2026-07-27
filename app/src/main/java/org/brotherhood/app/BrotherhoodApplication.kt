package org.brotherhood.app

import android.app.Application
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.brotherhood.app.crypto.CryptoEngine
import org.brotherhood.app.data.BrotherhoodRepository
import org.brotherhood.app.storage.SecureStateStore
import org.brotherhood.app.transport.LanTransport
import org.brotherhood.app.transport.TorTransport
import org.brotherhood.app.transport.TransportRouter
import org.brotherhood.app.transport.TransportRuntimeController
import org.brotherhood.app.transport.DeliveryEngine

class BrotherhoodApplication : Application() {
    lateinit var repository: BrotherhoodRepository
        private set
    lateinit var lanTransport: LanTransport
        private set
    lateinit var torTransport: TorTransport
        private set
    lateinit var transportRouter: TransportRouter
        private set
    lateinit var runtimeController: TransportRuntimeController
        private set
    lateinit var deliveryEngine: DeliveryEngine
        private set
    private val initializationMutex = Mutex()
    @Volatile
    private var initialized = false

    override fun onCreate() {
        super.onCreate()
        val crypto = CryptoEngine()
        repository = BrotherhoodRepository(SecureStateStore(this), crypto)
        lanTransport = LanTransport(this, repository)
        torTransport = TorTransport(this, repository)
        transportRouter = TransportRouter(repository, listOf(lanTransport, torTransport))
        runtimeController = TransportRuntimeController(lanTransport, torTransport)
        deliveryEngine = DeliveryEngine(repository, transportRouter)
    }

    suspend fun ensureInitialized() = initializationMutex.withLock {
        if (!initialized) {
            repository.initialize()
            initialized = true
        }
    }

    override fun onTerminate() {
        runBlocking {
            torTransport.stop()
            lanTransport.stop()
        }
        super.onTerminate()
    }
}
