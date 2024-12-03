plugins {
    alias(libs.plugins.android.library) apply false
	alias(libs.plugins.kotlin.multiplatform) apply false
	alias(libs.plugins.kotlin.android) apply false
	alias(libs.plugins.vanniktech.publish) apply false
	alias(libs.plugins.kotlinx.atomicfu) apply false
}

allprojects {
	group = property("GROUP").toString()
	version = getProjectVersion()
}

private fun getProjectVersion(): String {
	val versionFromGradleProperties = property("LOCAL_VERSION_NAME").toString()
	val versionFromWorkflow = runCatching { property("githubRefName").toString().removePrefix("v") }.getOrNull()
	return versionFromWorkflow ?: versionFromGradleProperties
}