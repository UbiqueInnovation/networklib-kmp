import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.android.library)
	alias(libs.plugins.vanniktech.publish)
	alias(libs.plugins.sqldelight)
	alias(libs.plugins.kotlinx.atomicfu)
	alias(libs.plugins.kotlinx.kover)
}

kotlin {
	jvmToolchain(17)

	jvm()

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

	js {
		browser {}
		nodejs {}
	}

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
			implementation(libs.ktor.client.mock)
			implementation(libs.ktor.client.cio)
		}
		androidMain.dependencies {
			implementation(libs.sqldelight.android.driver)
			implementation(libs.slf4j.nop)
		}
		iosMain.dependencies {
			implementation(libs.sqldelight.native.driver)
		}
		jvmMain.dependencies {
			implementation(libs.sqldelight.sqlite.driver)
			implementation(libs.slf4j.nop)
		}
		jvmTest
		jsMain.dependencies {
			implementation(libs.sqldelight.web.worker.driver)
			implementation(npm("sql.js", "1.8.0"))
			implementation(devNpm("copy-webpack-plugin", "9.1.0"))
			implementation(npm("@cashapp/sqldelight-sqljs-worker", "2.1.0"))
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
	compileSdk = 35
	defaultConfig {
		minSdk = 23
	}
}

sqldelight {
	databases {
		create("NetworkCacheDatabase") {
			packageName.set("ch.ubique.libs.ktor.cache.db")
		}
	}
}

tasks.withType(Test::class) {
	testLogging {
		setEvents(listOf("standardOut", "passed", "skipped", "failed"))
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
	}
}

mavenPublishing {
	coordinates(version = project.version.toString())
	publishToMavenCentral(true)
	signAllPublications()
}
