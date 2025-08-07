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
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        buildConfigField("String", "TUS_UPLOAD_ENDPOINT", "\"https://video.bunnycdn.com/tusupload\"")
        buildConfigField("String", "BASE_API", "\"https://video.bunnycdn.com\"")
        buildConfigField("String", "RTMP_ENDPOINT", "\"rtmp://49.13.154.169/ingest\"")

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


}

dependencies {
    // AndroidX and Material
    // https://developer.android.com/jetpack/androidx/releases/core
    implementation("androidx.core:core-ktx:1.15.0")
    // https://developer.android.com/jetpack/androidx/releases/appcompat
    implementation("androidx.appcompat:appcompat:1.7.0")
    // https://github.com/material-components/material-components-android
    implementation("com.google.android.material:material:1.12.0")

    // Testing dependencies
    // https://junit.org/junit4/
    testImplementation("junit:junit:4.13.2")
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

    // Arrow
    // https://arrow-kt.io
    implementation("io.arrow-kt:arrow-core:2.0.1")

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

val specs = File("$rootDir/bunny-stream-api/openapi").walk().map {
    Pair(it.name, it.path)
}.toMap().filter { it.key != "openapi" }

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
            "VideoModelStatus" to "net.bunny.api.model.VideoModelStatus"
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

tasks.register<Copy>("copyGeneratedDocs") {
    dependsOn("openApiGenerateAll")
    from(layout.buildDirectory.dir("generated/api/docs"))
    into(file("../docs"))
    doLast {
        logger.lifecycle("Successfully copied generated API docs to ../docs")
    }
}

// Needed to remove VideoModelStatus class which gets generated incorrectly.
// Correct implementation is supplied from net.bunny.api.model.VideoModelStatus.
tasks.register("fixGeneratedFiles") {
    doLast {
        val fileToFix = file("${layout.buildDirectory.dir("generated/api/").get().asFile.absolutePath}/src/main/kotlin/org/openapitools/client/models/VideoModelStatus.kt")
        if (fileToFix.exists()) {
            try {
                // Empty placeholder class that matches the package of the original file
                val content = """
                    /**
                     * This is a placehodler class. The actual implementation is provided by typeMappings in GenerateTask config.
                     */
                    package org.openapitools.client.models
                    
                    // This class replaces the (wrong) auto-generated implementation
                    class VideoModelStatus {
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

afterEvaluate {
    tasks.matching { it.name.endsWith("sourceReleaseJar") }
        .configureEach {
            dependsOn("openApiGenerateAll")
        }
}
