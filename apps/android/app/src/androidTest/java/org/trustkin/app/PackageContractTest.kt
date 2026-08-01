package org.trustkin.app

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class PackageContractTest {
    @Test
    fun installsUnderTheTrustKinApplicationId() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("org.trustkin.app", context.packageName)
    }
}
