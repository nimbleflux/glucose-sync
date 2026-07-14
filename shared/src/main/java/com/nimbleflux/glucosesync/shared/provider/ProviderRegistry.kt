package com.nimbleflux.glucosesync.shared.provider

import android.content.Context
import com.nimbleflux.glucosesync.shared.provider.dexcom.DexcomProvider
import com.nimbleflux.glucosesync.shared.provider.libre.LibreLinkUpProvider
import com.nimbleflux.glucosesync.shared.provider.medtrum.MedtrumProvider
import com.nimbleflux.glucosesync.shared.provider.nightscout.NightscoutProvider
import com.nimbleflux.glucosesync.shared.provider.xdrip.XdripBroadcastProvider

object ProviderRegistry {

    private val providers = listOf(
        ProviderConfig(
            id = "medtrum",
            displayName = "Medtrum EasyView",
            description = "Connect your Medtrum Nano sensor via EasyView cloud",
            authType = AuthType.USERNAME_PASSWORD,
            available = true,
            icon = "\uD83D\uDCF2",
            supportsLocalTrend = true
        ),
        ProviderConfig(
            id = "libre_linkup",
            displayName = "LibreLinkUp",
            description = "Connect via Abbott LibreLinkUp",
            authType = AuthType.USERNAME_PASSWORD,
            available = true,
            icon = "\uD83E\uDE78"
        ),
        ProviderConfig(
            id = "nightscout",
            displayName = "Nightscout",
            description = "Connect to your self-hosted Nightscout",
            authType = AuthType.API_TOKEN,
            available = true,
            icon = "\uD83C\uDF10"
        ),
        ProviderConfig(
            id = "dexcom_share",
            displayName = "Dexcom",
            description = "Dexcom retired the legacy Share login; a supported integration path is pending",
            authType = AuthType.USERNAME_PASSWORD,
            available = false,
            icon = "\uD83D\uDCE1"
        ),
        ProviderConfig(
            id = "xdrip",
            displayName = "xDrip+ (Direct Sensor)",
            description = "Read directly from your sensor via xDrip+ — no cloud needed",
            authType = AuthType.NONE,
            available = true,
            icon = "\uD83D\uDC0D",
            supportsLocalTrend = true
        )
    )

    fun availableProviders(): List<ProviderConfig> = providers

    fun getConfig(id: String): ProviderConfig? = providers.find { it.id == id }

    fun create(id: String, context: Context, debug: Boolean = false): GlucoseProvider {
        return when (id) {
            "medtrum" -> MedtrumProvider(context, debug)
            "libre_linkup" -> LibreLinkUpProvider(context, debug)
            "nightscout" -> NightscoutProvider(context, debug)
            "dexcom_share" -> DexcomProvider(context, debug)
            "xdrip" -> XdripBroadcastProvider(context, debug)
            else -> throw IllegalArgumentException("Unknown provider: $id")
        }
    }
}
