plugins {
    id("com.android.library")
    id("org.jetbrains.dokka")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
}

android {
    namespace = "net.bunny.recording"
    compileSdk = 36

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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14" // Replace with the correct version
    }


}



dependencies {
    // Project module dependency
    implementation(project(":api"))

    // AndroidX and Material
    // https://developer.android.com/jetpack/androidx/releases/core
    implementation("androidx.core:core-ktx:1.18.0")
    // https://developer.android.com/jetpack/androidx/releases/appcompat
    implementation("androidx.appcompat:appcompat:1.7.1")
    // https://github.com/material-components/material-components-android
    implementation("com.google.android.material:material:1.14.0")


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
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    // https://developer.android.com/jetpack/androidx/releases/test#espresso
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

    // Tus client libraries (update to newer patch versions if available)
    // https://github.com/pedroSG94/RootEncoder
    implementation("com.github.pedroSG94.RootEncoder:library:2.7.2")
    implementation("com.github.pedroSG94.RootEncoder:extra-sources:2.7.2")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

// API reference content. Dokka 2 runs in v2 mode (see gradle.properties), where every output is
// configured through this extension rather than per-task.
dokka {
    moduleName.set("BunnyStreamCameraUpload")
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
                uri("https://github.com/BunnyWay/bunny-stream-android/tree/main/bunny-stream-camera-upload/src/main/java")
            )
            remoteLineSuffix.set("#L")
        }
    }
}
