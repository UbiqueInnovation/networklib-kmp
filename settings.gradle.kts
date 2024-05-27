enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "networklib-kmp"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "kotlinx-atomicfu" -> useModule("org.jetbrains.kotlinx:atomicfu-gradle-plugin:${requested.version}")
            }
        }
    }
    dependencyResolutionManagement {
        repositories {
            google()
            mavenCentral()
        }
    }
}

include(":lib")
