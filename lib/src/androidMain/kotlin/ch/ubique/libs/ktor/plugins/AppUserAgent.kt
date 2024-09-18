package ch.ubique.libs.ktor.plugins

import android.content.Context
import android.os.Build

actual object AppUserAgentProvider {

	private var userAgentString: String? = null

	fun init(context: Context) {
		val packageName = context.packageName
		val versionCode = context.packageManager.getPackageInfo(packageName, 0).versionCode
		userAgentString = "Android/${Build.VERSION.SDK_INT} $packageName/$versionCode"
	}

	actual fun getUserAgentString(): String {
		return requireNotNull(userAgentString) { "Must call AppUserAgentProvider.init() in Application.onCreate()" }
	}

}
