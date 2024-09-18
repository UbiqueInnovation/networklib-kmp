package ch.ubique.libs.ktor.plugins

actual object AppUserAgentProvider {

	private var userAgentString: String

	init {
		val bundleIdentifier = platform.Foundation.NSBundle.mainBundle.infoDictionary?.get("CFBundleIdentifier")
		val appVersionCode = platform.Foundation.NSBundle.mainBundle.infoDictionary?.get("CFBundleVersion")
		val systemVersion = platform.UIKit.UIDevice.currentDevice.systemVersion
		userAgentString = "iOS/$systemVersion $bundleIdentifier/$appVersionCode"
	}

	actual fun getUserAgentString(): String {
		return userAgentString
	}

}
