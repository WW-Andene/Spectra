import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Read release-signing config from `keystore.properties` at the repo root
// if present. Gitignored — see keystore.properties.example for the schema.
// Without this file the release build falls back to debug signing so
// `assembleRelease` still produces an APK locally without ceremony.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.andene.spectra"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.andene.spectra"
        minSdk = 26 // Android 8.0+ for ConsumerIR + CameraX
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        viewBinding = true
    }

    lint {
        // Spectra ships en-only by design (CLAUDE.md + README declare it).
        // Suppress MissingTranslation so CI doesn't fail on the deliberate
        // single-locale stance. Re-enable if/when locales are added.
        disable.add("MissingTranslation")
        // abortOnError keeps the default (true): lint errors break the
        // build. Warnings stay warnings — promoting every warning to an
        // error makes the bar higher than the codebase currently meets
        // and would mask the real-error signal we want CI to surface.
        abortOnError = true
    }

    signingConfigs {
        // 'release' is created only when the keystore.properties file is
        // present + complete. Otherwise we leave it absent and the
        // release buildType falls through to debug signing — assembleRelease
        // still produces a runnable APK locally without secrets.
        if (keystoreProps.getProperty("storeFile")?.isNotBlank() == true) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            // R8 minification + resource shrinking. Strips Log.d/Log.v
            // statements (handled by ProGuard rules below), removes unused
            // resources, and slims the APK ~50% on this codebase. Debug
            // builds stay unminified for fast iteration.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Use the release signing config when available; fall back to
            // debug signing so assembleRelease keeps working in dev.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Lifecycle + ViewModel + Coroutines
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Navigation Component
    val navVersion = "2.7.7"
    implementation("androidx.navigation:navigation-fragment-ktx:$navVersion")
    implementation("androidx.navigation:navigation-ui-ktx:$navVersion")

    // CameraX (Module 1 — IR Camera Capture)
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Serialization (for device profile persistence)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // SplashScreen compat (Android 12+ native, polyfilled below)
    implementation("androidx.core:core-splashscreen:1.0.1")

    // NSD/mDNS — included in Android SDK, no extra dep

    // Unit tests (host JVM)
    testImplementation("junit:junit:4.13.2")
    // Robolectric runs Android-framework-touching code on the JVM (no
    // emulator). Used by repository round-trip and orchestrator-matcher
    // tests that need a Context.
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

// Robolectric needs the test runner to know the package + minSdk.
android {
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}
