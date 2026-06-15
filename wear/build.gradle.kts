plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.plugin.compose)
}

android {
    namespace = "com.nimbleflux.glucosesync.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nimbleflux.glucosesync"
        minSdk = 30
        targetSdk = 36
        versionCode = 100031
        versionName = "1.4.3"
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

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.play.services.wearable)
    implementation(libs.wear.watchface.complications.data)
    implementation(libs.wear.watchface.complications.data.source)
    implementation(libs.wear.tiles)
    implementation(libs.protolayout)
    implementation(libs.protolayout.material)
    implementation("com.google.guava:guava:33.6.0-jre")
}
