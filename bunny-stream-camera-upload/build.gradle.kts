plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.dokka")
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
}

android {
    namespace = "net.bunny.recording"
    compileSdk = 35

    viewBinding.enable = true

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


}

tasks.dokkaGfm {
    outputDirectory.set(file("docs"))
    dependsOn("compileDebugKotlin", "compileDebugSources")

    dokkaSourceSets {
        named("main") {
            moduleName.set("BunnyStreamCameraUpload")
        }
    }
}

dependencies {
    // Project module dependency
    implementation(project(":api"))

    // AndroidX and Material
    // https://developer.android.com/jetpack/androidx/releases/core
    implementation("androidx.core:core-ktx:1.15.0")
    // https://developer.android.com/jetpack/androidx/releases/appcompat
    implementation("androidx.appcompat:appcompat:1.7.0")
    // https://github.com/material-components/material-components-android
    implementation("com.google.android.material:material:1.12.0")


    // Jetpack Compose BOM for consistent versioning
    // https://developer.android.com/jetpack/compose/bom
    implementation(platform("androidx.compose:compose-bom:2025.03.01"))

    // Core Compose libraries (runtime, UI, etc.)
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")

    // Testing dependencies
    // https://junit.org/junit4/
    testImplementation("junit:junit:4.13.2")
    // https://developer.android.com/jetpack/androidx/releases/test
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    // https://developer.android.com/jetpack/androidx/releases/test#espresso
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // Functional programming library (Arrow)
    // https://arrow-kt.io
    implementation("io.arrow-kt:arrow-core:2.0.1")

    // Tus client libraries (update to newer patch versions if available)
    // https://github.com/pedroSG94/RootEncoder
    implementation("com.github.pedroSG94.RootEncoder:library:2.5.9")
    implementation("com.github.pedroSG94.RootEncoder:extra-sources:2.5.9")
}
