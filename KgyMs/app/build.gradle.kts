
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.kgyms.app"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.kgyms.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // Android Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

    // Android Splash Screen
    implementation("androidx.core:core-splashscreen:1.2.0")

    // Firebase BoM
    implementation(platform(libs.firebase.bom))

    // Firebase Authentication
    implementation(libs.firebase.auth)

    // Firebase Realtime Database
    implementation(libs.firebase.database)

    // Firebase Storage
    implementation(libs.firebase.storage)

    // QR: CameraX + ML Kit để quét, ZXing để vẽ mã VietQR
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.zxing.core)

    // Google Sign-In with Credential Manager
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Unit tests
    testImplementation(libs.junit)

    // Android tests
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // Debug tools
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
