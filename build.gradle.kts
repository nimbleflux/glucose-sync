plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0" apply false
}

extra["kotlinVersion"] = "2.1.21"

val composeBomVersion = "2025.12.01"
extra["composeBomVersion"] = composeBomVersion
extra["wearComposeVersion"] = "1.6.2"
extra["lifecycleVersion"] = "2.10.0"
extra["retrofitVersion"] = "3.0.0"
extra["okhttpVersion"] = "4.12.0"
extra["datastoreVersion"] = "1.2.1"
extra["wearableVersion"] = "19.0.0"
