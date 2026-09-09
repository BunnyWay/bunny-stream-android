plugins {
    id("com.android.library")
    id("kotlin-parcelize")
    id("org.jetbrains.dokka")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
}

android {
    namespace = "net.bunny.player"
    compileSdk = 36

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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14" // Replace with the correct version
    }
    viewBinding.enable = true

    buildFeatures {
        // Live-stream Compose surface ([BunnyLiveStreamPlayer]) needs the build feature on. The
        // pre-existing View-based player still works with view binding above.
        compose = true
    }

    testOptions {
        unitTests {
            // BunnyLiveStreamPlayerViewModel logs lifecycle and poll events via [android.util.Log].
            // Without [isReturnDefaultValues], AGP makes those calls throw "not mocked", which
            // bubbles out of the polling coroutine and trips the test as a bare RuntimeException.
            // Defaults are fine for tests — production logging is unchanged.
            isReturnDefaultValues = true
        }
    }
}



dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    // Project Module
    // https://docs.gradle.org/current/userguide/java_plugin.html#sec:project_dependencies
    implementation(project(":api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.google.code.gson:gson:2.14.0")
    // AndroidX and Material
    // https://developer.android.com/jetpack/androidx/releases/core
    implementation("androidx.core:core-ktx:1.18.0")
    // https://developer.android.com/jetpack/androidx/releases/appcompat
    implementation("androidx.appcompat:appcompat:1.7.1")
    // https://github.com/material-components/material-components-android
    implementation("com.google.android.material:material:1.14.0")

    // AndroidX Media3
    // https://developer.android.com/jetpack/androidx/releases/media3
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    // https://developer.android.com/jetpack/androidx/releases/media3
    implementation("androidx.media3:media3-ui:1.10.1")
    // https://developer.android.com/jetpack/androidx/releases/media3
    implementation("androidx.media3:media3-exoplayer-hls:1.10.1")
    // https://developer.android.com/jetpack/androidx/releases/media3
    implementation("androidx.media3:media3-exoplayer-dash:1.10.1")
    // https://developer.android.com/jetpack/androidx/releases/media3
    implementation("androidx.media3:media3-cast:1.10.1")
    // https://developer.android.com/jetpack/androidx/releases/media3
    implementation("androidx.media3:media3-exoplayer-ima:1.10.1")

    // Explicit (media3-cast also pulls them transitively) because the SDK
    // uses the Cast framework and MediaRouteButton directly.
    // https://developers.google.com/cast/docs/android_sender
    implementation("com.google.android.gms:play-services-cast-framework:22.1.0")
    // https://developer.android.com/jetpack/androidx/releases/mediarouter
    implementation("androidx.mediarouter:mediarouter:1.7.0")

    // AndroidX Startup
    // https://developer.android.com/jetpack/androidx/releases/startup
    implementation("androidx.startup:startup-runtime:1.2.0")

    // AndroidX Lifecycle
    // https://developer.android.com/jetpack/androidx/releases/lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    // https://developer.android.com/jetpack/androidx/releases/lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")

    // Testing Dependencies
    // https://junit.org/junit4/
    testImplementation("junit:junit:4.13.2")
    // Virtual-time + TestDispatcher for [BunnyLiveStreamPlayerViewModel] polling-rule tests.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    // https://developer.android.com/jetpack/androidx/releases/test
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    // https://developer.android.com/jetpack/androidx/releases/test#espresso
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

    // Glide for Image Loading
    // https://github.com/bumptech/glide
    implementation("com.github.bumptech.glide:glide:4.16.0")


    // YAML Parsing (Kaml)
    // https://github.com/charleskorn/kaml
    implementation("com.charleskorn.kaml:kaml:0.104.0")

    // Jetpack Compose Dependencies
    // https://developer.android.com/jetpack/compose/bom
    implementation(platform("androidx.compose:compose-bom:2025.03.01"))
    // https://developer.android.com/jetpack/compose/documentation
    implementation("androidx.compose.runtime:runtime")
    // https://developer.android.com/jetpack/compose/documentation
    implementation("androidx.compose.ui:ui")
    // Live-stream Compose surface — pulls in layout/foundation/material3/animation primitives
    // used by [BunnyLiveStreamPlayer]. Versioned via the BoM above; no explicit versions needed.
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-viewbinding")
    // collectAsStateWithLifecycle + LocalLifecycleOwner Compose interop.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    // viewModel() composable factory used by BunnyLiveStreamPlayer to obtain its VM.
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

// API reference content. Dokka 2 runs in v2 mode (see gradle.properties), where every output is
// configured through this extension rather than per-task.
dokka {
    moduleName.set("BunnyStreamPlayer")
    dokkaSourceSets.configureEach {
        // Dokka's Android adapter derives one source set per variant — debug, release, staging plus
        // the test ones — and never one called "main", so a `name != "main"` guard here suppresses
        // every source set and the module leaves the build empty ("Nothing to document",
        // sourceSets=[]). The variants all document the same src/main/java, so keep release and
        // suppress the rest; that also keeps source-link merging unambiguous.
        if (name != "release") suppress.set(true)
        // "release" is a build type, not something a reader of the reference cares about.
        displayName.set("android")
        includes.from("Module.md")
        sourceLink {
            localDirectory.set(file("src/main/java"))
            remoteUrl.set(
                uri("https://github.com/BunnyWay/bunny-stream-android/tree/main/bunny-stream-player/src/main/java")
            )
            remoteLineSuffix.set("#L")
        }
    }
}
