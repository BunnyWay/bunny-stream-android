// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Android plugins
    // https://developer.android.com/studio/releases/gradle-plugin
    id("com.android.application") version "8.8.1" apply false
    id("com.android.library") version "8.8.1" apply false

    // Kotlin plugins
    // https://kotlinlang.org/docs/gradle.html
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false

    // Code quality tools
    // https://github.com/detekt/detekt
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false

    // OpenAPI Generator (Never versions mess up markdown table generation)
    // https://openapi-generator.tech
    id("org.openapi.generator") version "7.6.0" apply false

    // Documentation
    // https://kotlin.github.io/dokka
    id("org.jetbrains.dokka") version "2.0.0"

    // Maven publishing
    id("signing") apply false
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
}

// Common configuration for all subprojects
allprojects {
    group = "net.bunny"
    version = "1.0.0"

    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

tasks.dokkaGfmMultiModule {
    moduleName.set("Bunny Stream Android API")
    outputDirectory.set(file("docs"))
}

// Configure Nexus publishing
nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(project.findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME"))
            password.set(project.findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD"))
        }
    }
}