package com.nimbleflux.glucosesync.shared.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryTest {

    @Test
    fun supportsLocalTrend_trueOnlyForProvidersThatDeriveTrendLocally() {
        // The "Trend sensitivity" setting only affects providers whose
        // snapshot reaches resolveTrend as UNKNOWN. Today: medtrum + xdrip.
        assertTrue(ProviderRegistry.getConfig("medtrum")!!.supportsLocalTrend)
        assertTrue(ProviderRegistry.getConfig("xdrip")!!.supportsLocalTrend)
    }

    @Test
    fun supportsLocalTrend_falseForServerTrendProviders() {
        // Libre/Dexcom/Nightscout pass a server trend through unchanged,
        // so the sensitivity setting is a no-op for them.
        assertFalse(ProviderRegistry.getConfig("libre_linkup")!!.supportsLocalTrend)
        assertFalse(ProviderRegistry.getConfig("dexcom_share")!!.supportsLocalTrend)
        assertFalse(ProviderRegistry.getConfig("nightscout")!!.supportsLocalTrend)
    }

    @Test
    fun getConfig_unknownId_returnsNull() {
        assertEquals(null, ProviderRegistry.getConfig("does-not-exist"))
    }

    @Test
    fun dexcom_isMarkedUnavailableUntilSupportedIntegrationExists() {
        // Dexcom retired the legacy Share username/password login (HTTP 500
        // ApplicationNotAuthenticated on every region). It is gated off in the
        // picker until a supported path (e.g. the v3 OAuth API) lands.
        assertFalse(ProviderRegistry.getConfig("dexcom_share")!!.available)
    }
}
