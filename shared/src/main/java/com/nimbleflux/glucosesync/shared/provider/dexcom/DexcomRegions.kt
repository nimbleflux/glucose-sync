package com.nimbleflux.glucosesync.shared.provider.dexcom

object DexcomRegions {
    const val US = "https://share2.dexcom.com/ShareWebServices/Services/"
    const val OUS = "https://shareous2.dexcom.com/ShareWebServices/Services/"

    fun urlForRegion(region: String): String = when (region) {
        "ous" -> OUS
        else -> US
    }
}
