package ch.ubique.libs.ktor.plugins

import android.content.Context
import android.os.Build

actual object AppUserAgentProvider {

	private var userAgentString: String? = null

	fun init(context: Context) {
		val appPackageName = context.packageName
		@Suppress("DEPRECATION")
		val appVersionCode = context.packageManager.getPackageInfo(appPackageName, 0).let {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else it.versionCode
		}
		val systemVersion = Build.VERSION.SDK_INT
		userAgentString = "Android/$systemVersion $appPackageName/$appVersionCode"
	}

	actual fun getUserAgentString(): String {
		return requireNotNull(userAgentString) { "Must call AppUserAgentProvider.init() in Application.onCreate()" }
	}

}
