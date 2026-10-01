plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.myfit.tracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.myfit.tracker"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "0.1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        vectorDrawables { useSupportLibrary = true }
        // API keys come from CI secrets (never committed); empty if not configured
        buildConfigField("String", "GEMINI_KEY", "\"" + (System.getenv("GEMINI_API_KEY") ?: "").trim() + "\"")
        buildConfigField("String", "ELEVEN_KEY", "\"" + (System.getenv("ELEVENLABS_API_KEY") ?: "").trim() + "\"")
        ndk { abiFilters += (System.getenv("MYFIT_ABIS") ?: "arm64-v8a").split(",") }
    }

    // Release signing comes from CI secrets (never committed). Without them the build falls back
    // to debug signing so it still produces an installable APK.
    val ksFile = System.getenv("MYFIT_KEYSTORE_FILE")?.let { file(it) }
    val ksPass = System.getenv("MYFIT_KEYSTORE_PASSWORD")
    val hasReleaseKey = ksFile != null && ksFile.exists() && !ksPass.isNullOrEmpty()

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = ksFile
                storePassword = ksPass
                keyAlias = System.getenv("MYFIT_KEY_ALIAS") ?: "myfit"
                keyPassword = ksPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-process:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.health.connect:connect-client:1.1.0")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // On-device neural voice (Supertonic via sherpa-onnx; AAR fetched by CI) + .tar.bz2 extraction
    implementation(files("libs/sherpa-onnx.aar"))
    implementation("org.apache.commons:commons-compress:1.27.1")

    testImplementation("junit:junit:4.13.2")
}
