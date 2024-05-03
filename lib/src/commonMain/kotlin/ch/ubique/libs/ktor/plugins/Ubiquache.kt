package ch.ubique.libs.ktor.plugins

import ch.ubique.libs.ktor.cache.db.CacheDatabaseDriverFactory
import ch.ubique.libs.ktor.cache.db.NetworkCacheDatabase
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.util.AttributeKey
import io.ktor.util.KtorDsl

class Ubiquache private constructor(val name: String) {

	/**
	 * Plugin to set the Accept-Language header for all requests.
	 *
	 * Configured by setting [language][Config.language] for a fixed value or [languageProvider][Config.languageProvider] for a dynamic value.
	 */
	companion object Plugin : HttpClientPlugin<Config, Ubiquache> {

		override val key: AttributeKey<Ubiquache> = AttributeKey("Ubiquache")

		override fun prepare(block: Config.() -> Unit): Ubiquache {
			val config = Config().apply(block)
			val name = config.name ?: "ubiquache"
			require(name.matches(Regex("[A-Za-z0-9._\\-]+"))) { "Cache name must only use A-Za-z0-9._-" }
			return Ubiquache(name)
		}

		override fun install(plugin: Ubiquache, scope: HttpClient) {
			NetworkCacheDatabase(CacheDatabaseDriverFactory().createDriver(plugin.name))
		}

	}

	@KtorDsl
	class Config {
		var name: String? = null
	}

}
