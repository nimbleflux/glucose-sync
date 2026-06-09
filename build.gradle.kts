plugins {
    id("com.android.application") version "8.11.0" apply false
    id("com.android.library") version "8.11.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21" apply false
}

extra["kotlinVersion"] = "2.1.21"

val composeBomVersion = "2025.06.01"
extra["composeBomVersion"] = composeBomVersion
extra["wearComposeVersion"] = "1.4.1"
extra["lifecycleVersion"] = "2.9.1"
extra["retrofitVersion"] = "3.0.0"
extra["okhttpVersion"] = "4.12.0"
extra["datastoreVersion"] = "1.1.7"
extra["wearableVersion"] = "19.0.0"
