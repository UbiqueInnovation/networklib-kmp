import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.androidLibrary)
	alias(libs.plugins.vanniktech.publish)
}

kotlin {
	jvmToolchain(17)

	androidTarget {
		compilations.all {
			kotlinOptions {
				jvmTarget = "17"
			}
		}

		publishLibraryVariants("release")
	}

	val xcf = XCFramework()
	listOf(
		iosX64(),
		iosArm64(),
		iosSimulatorArm64()
	).forEach {
		it.binaries.framework {
			baseName = "shared"
			xcf.add(this)
			isStatic = true
		}
	}
	applyDefaultHierarchyTemplate()

	sourceSets {
		commonMain.dependencies {
			implementation(libs.kotlinx.coroutines)
			implementation(libs.kotlinx.datetime)
			api(libs.kotlinx.serialization)
			api(libs.ktor.client.core)
			api(libs.ktor.client.content.negotiation)
			api(libs.ktor.client.auth)
			api(libs.ktor.serialization.kotlinx.json)
		}
		commonTest.dependencies {
			implementation(libs.kotlin.test)
			implementation(libs.parameterize)
			implementation(libs.ktor.client.mock)
		}
	}
}

android {
	namespace = "ch.ubique.libs.ktor"
	compileSdk = 34
	defaultConfig {
		minSdk = 26
	}
}

publishing {
	repositories {
		maven {
			url = uri(System.getenv("UB_ARTIFACTORY_URL_ANDROID") ?: extra["ubiqueMavenUrl"] as? String ?: "")
			credentials {
				username = System.getenv("UB_ARTIFACTORY_USER") ?: extra["ubiqueMavenUser"] as? String ?: ""
				password = System.getenv("UB_ARTIFACTORY_PASSWORD") ?: extra["ubiqueMavenPass"] as? String ?: ""
			}
		}
	}
}
