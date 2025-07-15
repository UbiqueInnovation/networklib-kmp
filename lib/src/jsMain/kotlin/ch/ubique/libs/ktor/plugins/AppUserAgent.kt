package ch.ubique.libs.ktor.plugins

actual object AppUserAgentProvider {
	actual fun getUserAgentString(): String {
		return ""
	}
}
