import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.plugins.signing.SigningExtension

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

    //id("signing")                      apply false
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0" apply false

    `maven-publish`
    signing
}

tasks.dokkaGfmMultiModule {
    moduleName.set("Bunny Stream Android API")
    outputDirectory.set(file("docs"))
}

// Default coordinates and plugin application for Android libraries
allprojects {
    group = "com.example.bunnystream"
    version = "1.0.0"
}

subprojects {
    // Only configure publishing in Android-library modules
    pluginManager.withPlugin("com.android.library") {
        pluginManager.withPlugin("maven-publish") {
            afterEvaluate {
                extensions.configure<PublishingExtension> {
                    publications {
                        create<MavenPublication>("release") {
                            from(components["release"])
                            groupId    = project.group.toString()
                            artifactId = project.name
                            version    = project.version.toString()
                        }
                    }
                    configure<SigningExtension> {
                        useInMemoryPgpKeys(
                            project.findProperty("signing.key")     as String?,
                            project.findProperty("signing.password") as String?
                        )
                        sign(publications["release"])
                    }
                    repositories {
                        maven {
                            name = "MavenCentral"
                            url  = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                            credentials {
                                username = findProperty("ossrhUsername") as String? ?: ""
                                password = findProperty("ossrhPassword") as String? ?: ""
                            }
                        }
                    }
                }
            }
        }
    }
}