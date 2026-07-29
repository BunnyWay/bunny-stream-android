import org.jetbrains.dokka.gradle.DokkaTaskPartial

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("io.gitlab.arturbosch.detekt")
    id("org.openapi.generator")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.dokka")
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
}

android {
    buildFeatures {
        buildConfig = true
    }

    sourceSets["main"].java.srcDir(layout.buildDirectory.dir("generated/api"))

    namespace = "net.bunny.api"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        buildConfigField("String", "TUS_UPLOAD_ENDPOINT", "\"https://video.bunnycdn.com/tusupload\"")
        buildConfigField("String", "BASE_API", "\"https://video.bunnycdn.com\"")
        // Core Platform API host (api.bunny.net). The Stream API (BASE_API) has no watermark
        // endpoint — the library watermark is managed here via
        // PUT/DELETE /videolibrary/{id}/watermark. See DefaultLiveStreamRepository.
        buildConfigField("String", "BASE_CORE_API", "\"https://api.bunny.net\"")
        // SDK version + User-Agent sent on every SDK request, e.g. "bunny-stream-android/1.3.2".
        // Sourced from the module version (resolved at build time; see root build.gradle.kts).
        buildConfigField("String", "SDK_VERSION", "\"${project.version}\"")
        buildConfigField("String", "USER_AGENT", "\"bunny-stream-android/${project.version}\"")
        buildConfigField("String", "RTMP_ENDPOINT", "\"rtmp://49.13.154.169/ingest\"")
        // Default RTMP ingest endpoint for *live streams* (Preview API), as shown in the
        // dashboard's "Primary ingest URL". The publish URL is built as
        // "$LIVE_RTMP_ENDPOINT/{streamKey}". Override at runtime via
        // BunnyStreamCameraUpload.liveIngestEndpoint if needed.
        buildConfigField("String", "LIVE_RTMP_ENDPOINT", "\"rtmp://global.rtmp.mediadelivery.net/live\"")

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
            buildConfigField("String", "BASE_API", "\"https://video.testfluffle.net\"")
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

    testOptions {
        unitTests {
            // PlaybackSpeedManager.parsePlaybackSpeeds() and other internal code paths call into
            // [android.util.Log] for diagnostics. By default AGP makes Android framework methods
            // throw "not mocked" in JVM tests; that RuntimeException gets swallowed by
            // [DefaultLiveStreamRepository.runApi]'s catch-all and surfaces to tests as a
            // confusing [Either.Left]. Returning defaults keeps the production code paths
            // exercised without dragging Robolectric into the dep graph.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // AndroidX and Material
    // https://developer.android.com/jetpack/androidx/releases/core
    implementation("androidx.core:core-ktx:1.15.0")
    // https://developer.android.com/jetpack/androidx/releases/appcompat
    implementation("androidx.appcompat:appcompat:1.7.0")
    // https://github.com/material-components/material-components-android
    implementation("com.google.android.material:material:1.14.0")

    // Testing dependencies
    // https://junit.org/junit4/
    testImplementation("junit:junit:4.13.2")
    // mockk — used to stub the generated [ManageLiveStreamsApi] in the live-stream repository
    // tests. The generated APIs are final classes; mockk handles those without extra config.
    testImplementation("io.mockk:mockk:1.13.13")
    // Virtual-time + TestDispatcher for the repository's withContext(coroutineDispatcher) path.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
    // MockEngine — drives BasicUploaderService's upload Flow through real Ktor machinery
    // (progress callbacks, status handling) without a server. Version tracks the ktor client below.
    testImplementation("io.ktor:ktor-client-mock:3.1.2")
    // https://developer.android.com/jetpack/androidx/releases/test
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    // https://developer.android.com/jetpack/androidx/releases/test#espresso
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // OkHttp (BOM and related libraries)
    // https://square.github.io/okhttp/
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")

    // Moshi
    // https://github.com/square/moshi
    implementation("com.squareup.moshi:moshi:1.15.2")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")

    // Android Lifecycle
    // https://developer.android.com/jetpack/androidx/releases/lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Gson
    // https://github.com/google/gson
    implementation("com.google.code.gson:gson:2.12.1")

    // Ktor
    // https://ktor.io
    implementation("io.ktor:ktor-client-okhttp:3.1.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.1.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.2")
    implementation("io.ktor:ktor-client-logging-jvm:3.1.2")


    // Tus client libraries (update to newer patch versions if available)
    // https://github.com/tus/tus-java-client
    implementation("io.tus.java.client:tus-java-client:0.5.0")
    // https://github.com/tus/tus-android-client
    implementation("io.tus.android.client:tus-android-client:0.1.11")
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")

}

detekt {
    config.from("$rootDir/bunny-stream-api/detekt.yml")
}

// Only real spec files — guards against stray files (.DS_Store, editor swap files, …)
// being fed to the generator and failing the build.
val specs = File("$rootDir/bunny-stream-api/openapi").walk()
    .filter { it.isFile && it.extension in setOf("yml", "yaml", "json") }
    .map { Pair(it.name, it.path) }
    .toMap()

specs.forEach {
    tasks.create("openApiGenerate-${it.key}", org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class) {
        generatorName.set("kotlin")
        inputSpec.set(it.value)
        outputDir.set(layout.buildDirectory.dir("generated/api").get().asFile.absolutePath)
        apiPackage.set("net.bunny.api.api")
        generateApiTests.set(false)
        generateModelTests.set(false)

        additionalProperties.set(mapOf(
            "kotlinEnums" to "true",
            "useEnumExtension" to "true"
        ))

        configOptions.set(mapOf(
            "dateLibrary" to "string",
            "serializationLibrary" to "gson",
        ))

        typeMappings.set(mapOf(
            "VideoModelStatus" to "net.bunny.api.model.VideoModelStatus",
            "LiveStreamModelStatus" to "net.bunny.api.model.LiveStreamStatus",
            "VideoModelSmartGenerateStatus" to "net.bunny.api.model.SmartGenerateStatus",
            "VideoPlayDataModelPreferredPlaybackSource" to "net.bunny.api.model.VideoPlaybackSource",
            // Per-feature smart-generate statuses added in spec v1.5.3 — same oneOf:[$ref enum]
            // shape as the status wrappers above, so redirect them to the shared enum too.
            "SmartGenerateFeaturesStatusModelTitle" to "net.bunny.api.model.SmartGenerateStatus",
            "SmartGenerateFeaturesStatusModelDescription" to "net.bunny.api.model.SmartGenerateStatus",
            "SmartGenerateFeaturesStatusModelChapters" to "net.bunny.api.model.SmartGenerateStatus",
            "SmartGenerateFeaturesStatusModelMoments" to "net.bunny.api.model.SmartGenerateStatus"
        ))
    }
}

tasks.register("openApiGenerateAll") {
    dependsOn(specs.map { "openApiGenerate-${it.key}" })
    finalizedBy("fixGeneratedFiles")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(
        "openApiGenerateAll",
        "copyGeneratedDocs"
    )
}

tasks.withType<DokkaTaskPartial> {
    dependsOn(
        "openApiGenerateAll",
        "copyGeneratedDocs"
    )
}

// API-reference content settings shared by every Dokka output (GFM and HTML).
// The generated OpenAPI client (org.openapitools.*) is an implementation detail:
// its REST surface is already documented by the generated Markdown under ../docs,
// so it is suppressed here to keep the reference focused on the hand-written API.
tasks.withType<org.jetbrains.dokka.gradle.AbstractDokkaLeafTask> {
    moduleName.set("BunnyStreamApi")
    // Android variant source sets (debug/release/staging) have no sources of their own but,
    // left unsuppressed, they break Dokka's source-link merging - only "main" should document.
    dokkaSourceSets.configureEach {
        if (name != "main") suppress.set(true)
    }
    dokkaSourceSets.configureEach {
        includes.from("Module.md")
        sourceLink {
            localDirectory.set(file("src/main/java"))
            remoteUrl.set(
                uri("https://github.com/BunnyWay/bunny-stream-android/tree/main/bunny-stream-api/src/main/java").toURL()
            )
            remoteLineSuffix.set("#L")
        }
        perPackageOption {
            matchingRegex.set("""org\.openapitools.*""")
            suppress.set(true)
        }
    }
}

tasks.register<Copy>("copyGeneratedDocs") {
    dependsOn("openApiGenerateAll")
    from(layout.buildDirectory.dir("generated/api/docs"))
    into(file("../docs"))
    doLast {
        logger.lifecycle("Successfully copied generated API docs to ../docs")
    }
}

// Needed to remove the {Model}Status wrappers that the OpenAPI generator emits
// for `status` properties using `oneOf` references. The correct implementations
// are supplied from net.bunny.api.model.* and wired in via typeMappings above.
tasks.register("fixGeneratedFiles") {
    doLast {
        val generatedRoot = layout.buildDirectory.dir("generated/api/").get().asFile.absolutePath
        val brokenWrappers = listOf(
            "VideoModelStatus",
            "LiveStreamModelStatus",
            "VideoModelSmartGenerateStatus",
            "VideoPlayDataModelPreferredPlaybackSource",
            // Empty `data class …()` wrappers the generator emits for the per-feature
            // smart-generate statuses added in spec v1.5.3 (won't compile as data classes).
            "SmartGenerateFeaturesStatusModelTitle",
            "SmartGenerateFeaturesStatusModelDescription",
            "SmartGenerateFeaturesStatusModelChapters",
            "SmartGenerateFeaturesStatusModelMoments"
        )

        brokenWrappers.forEach { className ->
            val fileToFix = file("$generatedRoot/src/main/kotlin/org/openapitools/client/models/$className.kt")
            if (fileToFix.exists()) {
                try {
                    // Empty placeholder class that matches the package of the original file
                    val content = """
                        /**
                         * This is a placeholder class. The actual implementation is provided by typeMappings in GenerateTask config.
                         */
                        package org.openapitools.client.models

                        // This class replaces the (wrong) auto-generated implementation
                        class $className {
                            // Intentionally left empty
                        }
                    """.trimIndent()
                    fileToFix.writeText(content)
                } catch (e: Exception) {
                    logger.error("Failed to modify file: ${fileToFix.absolutePath}", e)
                    //throw GradleException("Failed to modify generated file: ${fileToFix.absolutePath}", e)
                }
            } else {
                logger.lifecycle("fixGeneratedFiles: file not found: ${fileToFix.absolutePath}")
            }
        }
    }
}

afterEvaluate {
    tasks.matching { it.name.endsWith("sourceReleaseJar") }
        .configureEach {
            dependsOn("openApiGenerateAll")
        }
}
