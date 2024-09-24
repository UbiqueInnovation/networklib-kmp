package ch.ubique.libs.ktor.plugins

import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

actual object AppUserAgentProvider {

	private var userAgentString: String

	init {
		val appIdentifier = NSBundle.mainBundle.infoDictionary?.get("CFBundleIdentifier")
		val appVersionCode = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString")
		val systemVersion = UIDevice.currentDevice.systemVersion
		userAgentString = "iOS/$systemVersion $appIdentifier/$appVersionCode"
	}

	actual fun getUserAgentString(): String {
		return userAgentString
	}

}
