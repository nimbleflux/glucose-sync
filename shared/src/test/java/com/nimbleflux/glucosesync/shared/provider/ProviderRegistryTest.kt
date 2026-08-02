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
    fun dexcom_isAvailable() {
        // The by-id Share flow (AuthenticatePublisherAccount ->
        // LoginPublisherAccountById) is endpoint-identical to the Nightscout
        // production bridge (share2nightscout-bridge), validated against the
        // same AccountPasswordInvalid response for non-Share accounts.
        // Enabled for Share-enabled accounts with a classic username.
        // Known limitation: G7-era email-login accounts (notably in Europe)
        // authenticate via OAuth2/web-login and are unreachable via the Share
        // password API — the login screen surfaces this caveat, and affected
        // users are pointed at the xDrip+ provider.
        assertTrue(ProviderRegistry.getConfig("dexcom_share")!!.available)
    }
}
