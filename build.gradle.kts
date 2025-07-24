// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Android plugins
    // https://developer.android.com/studio/releases/gradle-plugin
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false

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

subprojects {
    // Only configure publishing in Android-library modules
    pluginManager.withPlugin("com.android.library") {
        pluginManager.withPlugin("maven-publish") {
            afterEvaluate {
                extensions.configure<PublishingExtension> {
                    publications {
                        create<MavenPublication>("release") {
                            from(components["release"])
                            groupId = project.group.toString()
                            artifactId = project.name
                            version = project.version.toString()
                        }
                    }
                    configure<SigningExtension> {
                        val rawKey = project.findProperty("signing.key") as String?
                        val password = project.findProperty("signing.password") as String?
                        useInMemoryPgpKeys(rawKey.orEmpty(), password.orEmpty())
                        sign(publications["release"])
                    }
                    repositories {
                        maven {
                            name = "GitHubPackages"
                            url = uri("https://maven.pkg.github.com/BunnyWay/bunny-stream-android")
                            credentials {
                                username = project.findProperty("gpr.user") as String?
                                    ?: System.getenv("GITHUB_ACTOR")
                                password = project.findProperty("gpr.key") as String?
                                    ?: System.getenv("GITHUB_TOKEN")
                            }
                        }
                    }
                }
            }
        }
    }
}