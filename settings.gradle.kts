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
        // The PDF engine used by Readium is only published on JitPack.
        // Limited to that one publisher so nothing else is fetched from there.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.marain87") }
        }
    }
}

rootProject.name = "EasyReader"

include(":app")
// Everything that talks to the Calibre content server lives in this one module.
include(":calibre")
