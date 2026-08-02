package com.nimbleflux.glucosesync.shared.provider.dexcom

object DexcomRegions {
    const val US = "https://share2.dexcom.com/ShareWebServices/Services/"
    const val OUS = "https://shareous1.dexcom.com/ShareWebServices/Services/"

    data class Region(val code: String, val displayName: String, val url: String)

    val all = listOf(
        Region("us", "United States", US),
        Region("ous", "Outside US (OUS/EU)", OUS)
    )

    fun urlForCode(code: String): String = all.find { it.code == code }?.url ?: US

    fun codeForUrl(url: String): String = all.find { it.url == url }?.code ?: "us"

    /**
     * Legacy region selector kept for back-compat with any stored `base_url`
     * value. Prefer [urlForCode] for new code paths.
     */
    fun urlForRegion(region: String): String = when (region) {
        "ous" -> OUS
        else -> US
    }
}
