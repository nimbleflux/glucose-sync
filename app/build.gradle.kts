plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.plugin.compose)
}

android {
    namespace = "com.nimbleflux.glucosesync.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nimbleflux.glucosesync"
        minSdk = 26
        targetSdk = 36
        versionCode = 37
        versionName = "1.4.9"
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

    lint {
        // NonObservableLocale flags String.format() inside @Composable
        // functions. The concern is that a runtime locale change won't
        // trigger recomposition. In practice these values re-render on
        // the next data update (every 60s) and the locale-change-while-
        // app-is-open scenario is extremely rare for a CGM app.
        warning += "NonObservableLocale"
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.fragment)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)
    implementation(libs.work.runtime.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.tooling)
}
