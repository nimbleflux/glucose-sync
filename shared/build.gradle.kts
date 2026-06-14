plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.nimbleflux.glucosesync.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
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
}

dependencies {
    val retrofitVersion = rootProject.extra["retrofitVersion"] as String
    val okhttpVersion = rootProject.extra["okhttpVersion"] as String
    val datastoreVersion = rootProject.extra["datastoreVersion"] as String
    val wearableVersion = rootProject.extra["wearableVersion"] as String

    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:$retrofitVersion")
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:$okhttpVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("androidx.datastore:datastore-preferences:$datastoreVersion")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("com.google.android.gms:play-services-wearable:$wearableVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:$okhttpVersion")
}
