package ch.ubique.libs.ktor.plugins

actual object AppUserAgentProvider {

	private var userAgentString: String

	init {
		val appIdentifier = platform.Foundation.NSBundle.mainBundle.infoDictionary?.get("CFBundleIdentifier")
		val appVersionCode = platform.Foundation.NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString")
		val systemVersion = platform.UIKit.UIDevice.currentDevice.systemVersion
		userAgentString = "iOS/$systemVersion $appIdentifier/$appVersionCode"
	}

	actual fun getUserAgentString(): String {
		return userAgentString
	}

}
