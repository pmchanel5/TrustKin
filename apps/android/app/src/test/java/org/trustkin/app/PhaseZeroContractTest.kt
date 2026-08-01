package org.trustkin.app

import kotlin.test.Test
import kotlin.test.assertEquals

class PhaseZeroContractTest {
    @Test
    fun usesTheNewProductIdentity() {
        assertEquals("TrustKin", PhaseZeroContract.PRODUCT_NAME)
        assertEquals("phase0-reset", PhaseZeroContract.ARCHITECTURE_PHASE)
    }
}
