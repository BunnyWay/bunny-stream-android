plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("org.jetbrains.dokka")
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
    id("maven-publish")
    id("signing")
}

android {
    namespace = "net.bunny.player"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
            initWith(getByName("debug"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14" // Replace with the correct version
    }
    viewBinding.enable = true

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

tasks.dokkaGfm {
    suppressObviousFunctions.set(true)
    outputDirectory.set(file("docs"))
    dependsOn("compileDebugKotlin", "compileDebugSources")

    dokkaSourceSets {
        named("main") {
            moduleName.set("BunnyStreamPlayer")
        }
    }
}

dependencies {
    // Project Module
    // https://docs.gradle.org/current/userguide/java_plugin.html#sec:project_dependencies
    implementation(project(":api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    implementation("com.google.code.gson:gson:2.8.9")
    // AndroidX and Material
    // https://developer.android.com/jetpack/androidx/releases/core
    implementation("androidx.core:core-ktx:1.15.0")
    // https://developer.android.com/jetpack/androidx/releases/appcompat
    implementation("androidx.appcompat:appcompat:1.7.0")
    // https://github.com/material-components/material-components-android
    implementation("com.google.android.material:material:1.12.0")

    // AndroidX Media3
    // https://developer.android.com/jetpack/androidx/releases/media3
    implementation("androidx.media3:media3-exoplayer:1.6.0")
    // https://developer.android.com/jetpack/androidx/releases/media3
    implementation("androidx.media3:media3-ui:1.6.0")
    // https://developer.android.com/jetpack/androidx/releases/media3
    implementation("androidx.media3:media3-exoplayer-hls:1.6.0")
    // https://developer.android.com/jetpack/androidx/releases/media3
    implementation("androidx.media3:media3-cast:1.6.0")
    // https://developer.android.com/jetpack/androidx/releases/media3
    implementation("androidx.media3:media3-exoplayer-ima:1.6.0")

    // AndroidX Startup
    // https://developer.android.com/jetpack/androidx/releases/startup
    implementation("androidx.startup:startup-runtime:1.2.0")

    // AndroidX Lifecycle
    // https://developer.android.com/jetpack/androidx/releases/lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    // https://developer.android.com/jetpack/androidx/releases/lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // Testing Dependencies
    // https://junit.org/junit4/
    testImplementation("junit:junit:4.13.2")
    // https://developer.android.com/jetpack/androidx/releases/test
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    // https://developer.android.com/jetpack/androidx/releases/test#espresso
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // Glide for Image Loading
    // https://github.com/bumptech/glide
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Functional Programming (Arrow)
    // https://arrow-kt.io
    implementation("io.arrow-kt:arrow-core:2.0.1")

    // YAML Parsing (Kaml)
    // https://github.com/charleskorn/kaml
    implementation("com.charleskorn.kaml:kaml:0.74.0")

    // Jetpack Compose Dependencies
    // https://developer.android.com/jetpack/compose/bom
    implementation(platform("androidx.compose:compose-bom:2025.03.01"))
    // https://developer.android.com/jetpack/compose/documentation
    implementation("androidx.compose.runtime:runtime")
    // https://developer.android.com/jetpack/compose/documentation
    implementation("androidx.compose.ui:ui")
}
