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
    }
}

rootProject.name = "Bunny Stream"
include(":app")
include(":bunny-stream-api")
include(":bunny-stream-player")
include(":bunny-stream-camera-upload")

project(":bunny-stream-api").name = "api"
project(":bunny-stream-player").name = "player"
project(":bunny-stream-camera-upload").name = "recording"
