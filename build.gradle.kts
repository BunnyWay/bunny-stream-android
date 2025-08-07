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

    // Maven publishing
    // https://github.com/vanniktech/gradle-maven-publish-plugin
    id("com.vanniktech.maven.publish") version "0.34.0" apply false
}

tasks.dokkaGfmMultiModule {
    moduleName.set("Bunny Stream Android API")
    outputDirectory.set(file("docs"))
}

subprojects {
    // Only configure publishing in Android-library modules
    pluginManager.withPlugin("com.android.library") {
        // Apply the vanniktech maven publish plugin to each library module
        pluginManager.apply("com.vanniktech.maven.publish")
        
        // Configure the plugin with common settings
        extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            // Configure publishing to Maven Central via Sonatype Central Portal with automatic release
            publishToMavenCentral(automaticRelease = true)
            
            // Enable GPG signing for all publications
            signAllPublications()
            
            // Configure POM metadata
            pom {
                name.set("Bunny Stream Android SDK - ${project.name}")
                description.set("Bunny Stream Android SDK - ${project.name} module")
                url.set("https://github.com/BunnyWay/bunny-stream-android")
                
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://github.com/BunnyWay/bunny-stream-android/blob/main/LICENSE")
                    }
                }
                
                developers {
                    developer {
                        id.set("bunnyway")
                        name.set("BunnyWay")
                        email.set("support@bunny.net")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/BunnyWay/bunny-stream-android.git")
                    developerConnection.set("scm:git:ssh://github.com/BunnyWay/bunny-stream-android.git")
                    url.set("https://github.com/BunnyWay/bunny-stream-android")
                }
            }
        }
    }
}

// Resolve version from env/props (no failure by default)
val resolvedVersionProvider = providers
    .environmentVariable("VERSION")
    .orElse(providers.gradleProperty("releaseVersion"))
    .orElse(providers.gradleProperty("version"))
    .map { it.trim() }
val resolvedVersion = resolvedVersionProvider.orNull

// Only enforce in CI when explicitly requested
val enforceReleaseVersion = providers
    .gradleProperty("enforceVersion")                 // -PenforceVersion=true
    .orElse(providers.environmentVariable("ENFORCE_VERSION"))
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
    .get()

if (enforceReleaseVersion) {
    require(!resolvedVersion.isNullOrBlank()) {
        "Project version is empty. Provide VERSION env var (from tag) or -PreleaseVersion/-Pversion."
    }
}

// Configuration for vanniktech maven publish plugin
// The plugin will automatically handle Maven Central publishing via Sonatype Central Portal
// Environment variables needed:
// - ORG_GRADLE_PROJECT_mavenCentralUsername: Central Portal username 
// - ORG_GRADLE_PROJECT_mavenCentralPassword: Central Portal password 
// - ORG_GRADLE_PROJECT_signingInMemoryKey: PGP key 
// - ORG_GRADLE_PROJECT_signingInMemoryKeyPassword: PGP key password 
// - ORG_GRADLE_PROJECT_signingInMemoryKeyId: PGP key ID

allprojects {
    group = "net.bunny"
    version = resolvedVersion ?: "1.0.0-SNAPSHOT"     // safe default for tests/other jobs
}

tasks.register("printAllGroups") {
    group = "help"
    description = "Prints each subproject's default group"
    doLast {
        rootProject.allprojects.forEach { p ->
            println("→ ${p.path}: group='${p.group}'")
        }
    }
}