plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("io.github.takahirom.roborazzi")
}

// Online features (accounts, friends, leaderboards) need app/google-services.json, written by CI from a secret.
// Without it the app still builds and simply hides those features.
val socialEnabled = file("google-services.json").exists()
if (socialEnabled) apply(plugin = "com.google.gms.google-services")

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
        buildConfigField("String", "GROQ_KEY", "\"" + (System.getenv("GROQ_API_KEY") ?: "").trim() + "\"")
        buildConfigField("String", "OPENROUTER_KEY", "\"" + (System.getenv("OPENROUTER_API_KEY") ?: "").trim() + "\"")
        buildConfigField("String", "MISTRAL_KEY", "\"" + (System.getenv("MISTRAL_API_KEY") ?: "").trim() + "\"")
        buildConfigField("String", "AZURE_SPEECH_KEY", "\"" + (System.getenv("AZURE_SPEECH_KEY") ?: "").trim() + "\"")
        buildConfigField("boolean", "SOCIAL", socialEnabled.toString())
        buildConfigField("String", "AZURE_SPEECH_REGION", "\"" + (System.getenv("AZURE_SPEECH_REGION") ?: "").trim() + "\"")
        // Rewarded ads: Google's public TEST ids unless the ADMOB_* secrets are set in CI
        val admobApp = (System.getenv("ADMOB_APP_ID") ?: "").trim().ifEmpty { "ca-app-pub-3940256099942544~3347511713" }
        val admobRewarded = (System.getenv("ADMOB_REWARDED_ID") ?: "").trim().ifEmpty { "ca-app-pub-3940256099942544/5224354917" }
        manifestPlaceholders["admobAppId"] = admobApp
        buildConfigField("String", "ADMOB_REWARDED_ID", "\"" + admobRewarded + "\"")
        ndk { abiFilters += (System.getenv("MYFIT_ABIS") ?: "arm64-v8a").split(",") }
    }

    // Release signing comes from CI secrets (never committed). Local / branch builds without them fall back
    // to debug signing; builds on main fail instead (see buildTypes.release).
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

    // UI screenshot tests (Robolectric + Roborazzi): app/src/test, run by .github/workflows/ui-shots.yml
    testOptions { unitTests { isIncludeAndroidResources = true; all { t ->
        t.testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL; showStandardStreams = false }
        // the screenshot test runs in its own workflow (ui-shots.yml), not in every APK build
        if (!project.hasProperty("shots")) t.exclude("**/*ShotTest*")
    } } }
    buildTypes {
        release {
            isMinifyEnabled = false
            // a published release must never be debug-signed (users couldn't update over it): fail loudly on main
            if (!hasReleaseKey && System.getenv("GITHUB_REF") == "refs/heads/main")
                throw GradleException("Release signing secrets (MYFIT_KEYSTORE_*) are missing — refusing to publish a debug-signed release")
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn", "-Xskip-metadata-version-check")
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
    // on-device LLM (Gemma via LiteRT-LM; the model itself is an optional in-app download, never bundled)
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1")
    // rewarded ads (only after the free daily AI allowance; SDK started on first tap)
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("androidx.health.connect:connect-client:1.1.0")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // On-device neural voice (Supertonic via sherpa-onnx; AAR fetched by CI) + .tar.bz2 extraction
    implementation(files("libs/sherpa-onnx.aar"))
    implementation("org.apache.commons:commons-compress:1.27.1")
    // Google code scanner: barcode scanning UI without a camera permission
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.zxing:core:3.5.3")
    // food camera: live preview, on-device food detection
    val camerax = "1.4.2"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.google.mlkit:image-labeling:17.0.9")
    // accounts, friends & leaderboards
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.40.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.40.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // CameraX / WorkManager futures expose Guava's ListenableFuture (the androidx stub artifact is empty) — keep
    implementation("com.google.guava:guava:33.3.1-android")
}
