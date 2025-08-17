pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven {
            url = uri("https://maven.pkg.github.com/BunnyWay/bunny-stream-android")
            credentials {
                // Use environment variables for GitHub Packages authentication
                // Ensure GITHUB_ACTOR and GITHUB_TOKEN are set in your environment
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                            ?: ""
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
                    ?: System.getenv("GITHUB_TOKEN")
                            ?: ""
            }
        }
    }
}

rootProject.name = "Bunny Stream"
include(":app")
include(":bunny-stream-api")
include(":bunny-stream-player")
include(":bunny-stream-camera-upload")
include(":bunny-stream-tv")

project(":bunny-stream-api").name = "api"
project(":bunny-stream-player").name = "player"
project(":bunny-stream-camera-upload").name = "recording"
project(":bunny-stream-tv").name = "tv"
