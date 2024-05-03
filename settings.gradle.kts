enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "networklib-kmp"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    dependencyResolutionManagement {
        repositories {
            google()
            mavenCentral()
        }
    }
}

include(":lib")
