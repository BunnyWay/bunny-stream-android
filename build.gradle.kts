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

    // Use the new Central Portal publishing with better alternative plugin
    id("com.gradleup.nmcp") version "1.0.2" apply false
    id("com.gradleup.nmcp.aggregation") version "1.0.2"
    
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
        // Apply the NMCP plugin to each library module for Central Portal publishing
        pluginManager.apply("com.gradleup.nmcp")
        pluginManager.withPlugin("maven-publish") {
            afterEvaluate {
                extensions.configure<PublishingExtension> {
                    publications {
                        create<MavenPublication>("release") {
                            from(components["release"])
                            groupId = project.group.toString()
                            artifactId = project.name
                            version = project.version.toString()
                            
                            // POM metadata required for Maven Central
                            pom {
                                name.set(project.name)
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

// Configure NMCP aggregation for Central Portal publishing
nmcpAggregation {
    centralPortal {
        username = System.getenv("CENTRAL_PORTAL_TOKEN_USERNAME")
        password = System.getenv("CENTRAL_PORTAL_TOKEN_PASSWORD")
        publishingType = "AUTOMATIC"
    }
    
    // Publish all projects that apply the 'maven-publish' plugin
    publishAllProjectsProbablyBreakingProjectIsolation()
}

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