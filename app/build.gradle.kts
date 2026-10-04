plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.wordbook"
    compileSdk = 34

    // Without this, every CI build signs the APK with a fresh throwaway key, and
    // Android refuses to install an "update" whose signature doesn't match what's
    // already on the phone — forcing an uninstall every time. Pinning the debug
    // build to this committed keystore keeps the signature identical across builds,
    // so updates install cleanly over the previous version from here on.
    signingConfigs {
        getByName("debug") {
            storeFile = file("wordbook-debug.keystore")
            storePassword = "wordbook123"
            keyAlias = "wordbook"
            keyPassword = "wordbook123"
        }
    }

    defaultConfig {
        applicationId = "app.wordbook"
        minSdk = 26
        // Kept at 34 on purpose: 35+ forces edge-to-edge layout, which this simple WebView shell doesn't handle.
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.11.0")
}
