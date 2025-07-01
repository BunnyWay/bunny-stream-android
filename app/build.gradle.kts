plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
    id("kotlin-parcelize")
}

android {
    namespace = "net.bunnystream.android.demo"
    compileSdk = 35

    viewBinding.enable = true

    defaultConfig {
        applicationId = "net.bunnystream.android.demo"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        getByName("debug") {

        }

        create("staging") {

        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.3"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Project Modules
    // Gradle Project Dependencies: https://docs.gradle.org/current/userguide/java_plugin.html#sec:project_dependencies
    implementation(project(":api"))
    implementation(project(":player"))
    implementation(project(":recording"))

    // AndroidX Core and Lifecycle
    // Core: https://developer.android.com/jetpack/androidx/releases/core
    implementation("androidx.core:core-ktx:1.15.0")
    // Lifecycle: https://developer.android.com/jetpack/androidx/releases/lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // AndroidX Activity
    // https://developer.android.com/jetpack/androidx/releases/activity
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.activity:activity-compose:1.10.1")

    // Jetpack Compose BOM and Dependencies
    // BOM: https://developer.android.com/jetpack/compose/bom
    implementation(platform("androidx.compose:compose-bom:2025.03.01"))
    // Compose UI: https://developer.android.com/jetpack/compose/documentation
    implementation("androidx.compose.ui:ui")
    // Compose Graphics: https://developer.android.com/jetpack/compose/documentation
    implementation("androidx.compose.ui:ui-graphics")
    // Compose Tooling Preview: https://developer.android.com/jetpack/compose/documentation
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Material3: https://m3.material.io/develop/android
    implementation("androidx.compose.material3:material3")
    // AppCompat (for compatibility with legacy views): https://developer.android.com/jetpack/androidx/releases/appcompat
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Testing Dependencies
    // JUnit: https://junit.org/junit4/
    testImplementation("junit:junit:4.13.2")
    // AndroidX Test JUnit: https://developer.android.com/jetpack/androidx/releases/test
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    // Espresso: https://developer.android.com/jetpack/androidx/releases/test#espresso
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    // Compose Testing BOM: https://developer.android.com/jetpack/compose/bom
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.03.01"))
    // Compose UI Test JUnit4: https://developer.android.com/jetpack/compose/documentation
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // Debug Compose Tooling: https://developer.android.com/jetpack/compose/documentation
    debugImplementation("androidx.compose.ui:ui-tooling")
    // Debug Compose Test Manifest: https://developer.android.com/jetpack/compose/documentation
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Constraint Layout
    // https://developer.android.com/jetpack/androidx/releases/constraintlayout
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    // Media3 (ExoPlayer, UI, HLS, Cast)
    // https://developer.android.com/jetpack/androidx/releases/media3
    implementation("androidx.media3:media3-exoplayer:1.6.0")
    implementation("androidx.media3:media3-ui:1.6.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.6.0")
    implementation("androidx.media3:media3-cast:1.6.0")

    // Navigation and Lifecycle for Compose
    // Navigation Compose: https://developer.android.com/jetpack/androidx/releases/navigation
    implementation("androidx.navigation:navigation-compose:2.8.9")
    // Lifecycle Compose: https://developer.android.com/jetpack/androidx/releases/lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Coil for Compose Image Loading
    // https://coil-kt.github.io/coil/compose/
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")

    // Accompanist Swipe Refresh
    // https://central.sonatype.com/artifact/com.google.accompanist/accompanist-swiperefresh
    implementation("com.google.accompanist:accompanist-swiperefresh:0.36.0")

    // Functional Programming (Arrow)
    // https://arrow-kt.io
    implementation("io.arrow-kt:arrow-core:2.0.1")
}
