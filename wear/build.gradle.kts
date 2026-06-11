plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nimbleflux.glucosesync.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nimbleflux.glucosesync"
        minSdk = 30
        targetSdk = 36
        versionCode = 100022
        versionName = "1.3.18"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "../keys/nimbleflux.jks")
            storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").getOrElse("")
            keyAlias = providers.environmentVariable("KEY_ALIAS").getOrElse("nimbleflux")
            keyPassword = providers.environmentVariable("KEY_PASSWORD").getOrElse("")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))

    val composeBomVersion = rootProject.extra["composeBomVersion"] as String
    val wearComposeVersion = rootProject.extra["wearComposeVersion"] as String
    val lifecycleVersion = rootProject.extra["lifecycleVersion"] as String
    val wearableVersion = rootProject.extra["wearableVersion"] as String

    implementation(platform("androidx.compose:compose-bom:$composeBomVersion"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.wear.compose:compose-material:$wearComposeVersion")
    implementation("androidx.wear.compose:compose-foundation:$wearComposeVersion")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")

    implementation("com.google.android.gms:play-services-wearable:$wearableVersion")
    implementation("androidx.wear.watchface:watchface-complications-data:1.3.0")
    implementation("androidx.wear.watchface:watchface-complications-data-source:1.3.0")
}
