package ch.ubique.libs.ktor.plugins

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.util.AttributeKey
import io.ktor.util.KtorDsl

class AcceptLanguage private constructor(val languageProvider: () -> String) {

	@KtorDsl
	class Config {
		var language: String? = null
		var languageProvider: () -> String = {
			requireNotNull(language) { "AcceptLanguage: `language` or `languageProvider` must be set." }
		}
	}

	companion object Plugin : HttpClientPlugin<Config, AcceptLanguage> {

		override val key: AttributeKey<AcceptLanguage> = AttributeKey("AcceptLanguagePlugin")

		override fun prepare(block: Config.() -> Unit): AcceptLanguage {
			val config = Config().apply(block)
			return AcceptLanguage(config.languageProvider)
		}

		override fun install(plugin: AcceptLanguage, scope: HttpClient) {
			scope.requestPipeline.intercept(HttpRequestPipeline.State) {
				context.header(HttpHeaders.AcceptLanguage, plugin.languageProvider())
			}
		}

	}
}
