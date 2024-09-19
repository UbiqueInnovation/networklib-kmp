package ch.ubique.libs.ktor.plugins

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.UserAgent

/**
 * Installs the [UserAgent] plugin with a system and app identifier.
 */
fun HttpClientConfig<*>.AppUserAgent() {
	install(UserAgent) {
		agent = AppUserAgentProvider.getUserAgentString()
	}
}

expect object AppUserAgentProvider {
	fun getUserAgentString(): String
}
