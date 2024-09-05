import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.android.library)
	alias(libs.plugins.vanniktech.publish)
	alias(libs.plugins.sqldelight)
	alias(libs.plugins.kotlinx.atomicfu)
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
			baseName = "network"
			isStatic = true
			xcf.add(this)
		}
	}
	applyDefaultHierarchyTemplate()

	sourceSets {
		commonMain.dependencies {
			implementation(libs.kotlinx.coroutines)
			implementation(libs.kotlinx.datetime)
			implementation(libs.kotlinx.atomicfu.dependency)
			api(libs.kotlinx.serialization)
			api(libs.ktor.client.core)
			api(libs.ktor.client.content.negotiation)
			api(libs.ktor.client.auth)
			api(libs.ktor.serialization.kotlinx.json)
			implementation(libs.sqldelight.runtime)
		}
		commonTest.dependencies {
			implementation(libs.kotlin.test)
			implementation(libs.kotlinx.coroutines.test)
			implementation(libs.turbine)
			implementation(libs.parameterize)
			implementation(libs.ktor.client.mock)
		}
		androidMain.dependencies {
			implementation(libs.sqldelight.android.driver)
		}
		iosMain.dependencies {
			implementation(libs.sqldelight.native.driver)
		}
	}

	targets.all {
		compilations.all {
			compilerOptions.configure {
				freeCompilerArgs.add("-Xexpect-actual-classes")
			}
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

sqldelight {
	databases {
		create("NetworkCacheDatabase") {
			packageName.set("ch.ubique.libs.ktor.cache.db")
		}
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
