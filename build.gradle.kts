plugins {
    id("com.android.application") version "9.2.1" apply false
    id("com.android.library") version "9.2.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0" apply false
}

extra["kotlinVersion"] = "2.1.21"

val composeBomVersion = "2026.05.01"
extra["composeBomVersion"] = composeBomVersion
extra["wearComposeVersion"] = "1.6.2"
extra["lifecycleVersion"] = "2.10.0"
extra["retrofitVersion"] = "3.0.0"
extra["okhttpVersion"] = "5.4.0"
extra["datastoreVersion"] = "1.2.1"
extra["wearableVersion"] = "20.0.1"
