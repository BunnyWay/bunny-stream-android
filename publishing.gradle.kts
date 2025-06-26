apply(plugin = "maven-publish")
apply(plugin = "signing")

// Configure publishing
afterEvaluate {
    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                // Customize the publication
                pom {
                    name.set(project.name)
                    description.set("Bunny Stream Android SDK - ${project.description ?: project.name}")
                    url.set("https://github.com/BunnyWay/bunny-stream-android")
                    
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    
                    developers {
                        developer {
                            id.set("bunnyway")
                            name.set("Bunny.net")
                            email.set("support@bunny.net")
                        }
                    }
                    
                    scm {
                        connection.set("scm:git:git://github.com/BunnyWay/bunny-stream-android.git")
                        developerConnection.set("scm:git:ssh://github.com:BunnyWay/bunny-stream-android.git")
                        url.set("https://github.com/BunnyWay/bunny-stream-android/tree/main")
                    }
                }
            }
        }
    }
    
    configure<SigningExtension> {
        val signingKey = project.findProperty("signing.key") as String? ?: System.getenv("MAVEN_KEY")
        val signingPassword = project.findProperty("signing.password") as String? ?: System.getenv("MAVEN_KEY_PASSWORD")
        
        if (signingKey != null && signingPassword != null) {
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(the<PublishingExtension>().publications["release"])
        }
    }
}

// Generate Javadoc JAR
tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(tasks.named("dokkaJavadoc"))
}

// Generate sources JAR
tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(extensions.getByType<com.android.build.gradle.LibraryExtension>().sourceSets.named("main").get().java.srcDirs)
}

// Configure artifacts
afterEvaluate {
    configure<PublishingExtension> {
        publications.named<MavenPublication>("release") {
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])
        }
    }
} 