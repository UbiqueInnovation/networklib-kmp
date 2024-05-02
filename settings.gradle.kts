enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

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
