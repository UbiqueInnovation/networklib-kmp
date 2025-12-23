package ch.ubique.libs.ktor.plugins

import platform.Foundation.NSBundle
import platform.WatchKit.WKInterfaceDevice

actual object AppUserAgentProvider {

	private var userAgentString: String

	init {
		val appIdentifier = NSBundle.mainBundle.infoDictionary?.get("CFBundleIdentifier")
		val appVersionCode = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString")
		val systemVersion = WKInterfaceDevice.currentDevice().systemVersion
		userAgentString = "watchOS/$systemVersion $appIdentifier/$appVersionCode"
	}

	actual fun getUserAgentString(): String {
		return userAgentString
	}

}
