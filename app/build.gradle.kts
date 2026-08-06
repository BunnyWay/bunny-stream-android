import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    id("kotlin-parcelize")
}

// Demo credentials are read from local.properties (git-ignored) or the
// environment — never committed to source. Release builds always ship empty
// so no API key is embedded in a distributable APK.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val demoAccessKey: String =
    localProps.getProperty("bunny.demo.accessKey")
        ?: System.getenv("BUNNY_DEMO_ACCESS_KEY")
        ?: ""
val demoLibraryId: String =
    localProps.getProperty("bunny.demo.libraryId")
        ?: System.getenv("BUNNY_DEMO_LIBRARY_ID")
        ?: "0"
// Token-authentication key (the library's "Token authentication key"). Lets the demo sign playback
// tokens when token auth is enabled. DEBUG ONLY — never ship this private key in a real app;
// generate tokens server-side instead.
val demoTokenAuthKey: String =
    localProps.getProperty("bunny.demo.tokenAuthKey")
        ?: System.getenv("BUNNY_DEMO_TOKEN_AUTH_KEY")
        ?: ""

android {
    namespace = "net.bunny.android.demo"
    compileSdk = 36

    viewBinding.enable = true

    defaultConfig {
        applicationId = "net.bunny.android.demo"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "1.19"

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
            // No API key embedded in release builds.
            buildConfigField("String", "DEMO_ACCESS_KEY", "\"\"")
            buildConfigField("long", "DEMO_LIBRARY_ID", "0L")
            buildConfigField("String", "DEMO_TOKEN_AUTH_KEY", "\"\"")
        }

        getByName("debug") {
            buildConfigField("String", "DEMO_ACCESS_KEY", "\"$demoAccessKey\"")
            buildConfigField("long", "DEMO_LIBRARY_ID", "${demoLibraryId}L")
            buildConfigField("String", "DEMO_TOKEN_AUTH_KEY", "\"$demoTokenAuthKey\"")
        }

        create("staging") {
            buildConfigField("String", "DEMO_ACCESS_KEY", "\"$demoAccessKey\"")
            buildConfigField("long", "DEMO_LIBRARY_ID", "${demoLibraryId}L")
            buildConfigField("String", "DEMO_TOKEN_AUTH_KEY", "\"$demoTokenAuthKey\"")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
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
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    // Project Modules
    implementation(project(":api"))
    implementation(project(":player"))
    implementation(project(":recording"))
    implementation(project(":tv"))

    // Lean Back
    implementation("androidx.leanback:leanback:1.2.0")

    // AndroidX Core and Lifecycle
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")

    // AndroidX Activity
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Jetpack Compose BOM and Dependencies
    implementation(platform("androidx.compose:compose-bom:2025.03.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Extended icon pack — ContentCopy & co. for the live stream links summary.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.appcompat:appcompat:1.7.1")

    // Testing Dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.03.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Constraint Layout
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    // Media3 (ExoPlayer, UI, HLS, Cast)
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.10.1")
    implementation("androidx.media3:media3-cast:1.10.1")

    // Navigation and Lifecycle for Compose
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    // Coil for Compose Image Loading
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")

    // Accompanist Swipe Refresh
    implementation("com.google.accompanist:accompanist-swiperefresh:0.36.0")

    // Functional Programming (Arrow)

    // Work Manager for background tasks
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // JSON processing - Essential for our position storage
    implementation("com.google.code.gson:gson:2.14.0")

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}
