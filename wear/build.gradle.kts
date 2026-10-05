plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Galaxy Watch / Wear OS companion: a "Today" tile (steps, water, calories) fed by the phone over the Wearable
// Data Layer. Same applicationId and signing key as the phone app — that's what lets the two talk.
android {
    namespace = "com.myfit.tracker.wear"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.myfit.tracker"
        minSdk = 30
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "0.1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }
    val ksFile = System.getenv("MYFIT_KEYSTORE_FILE")?.let { file(it) }
    val ksPass = System.getenv("MYFIT_KEYSTORE_PASSWORD")
    val hasReleaseKey = ksFile != null && ksFile.exists() && !ksPass.isNullOrEmpty()
    signingConfigs {
        if (hasReleaseKey) create("release") {
            storeFile = ksFile; storePassword = ksPass; keyAlias = System.getenv("MYFIT_KEY_ALIAS") ?: "myfit"; keyPassword = ksPass
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("androidx.wear.protolayout:protolayout-expression:1.2.1")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("com.google.guava:guava:33.3.1-android")
}
