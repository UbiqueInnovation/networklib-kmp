package ch.ubique.libs.ktor.common

actual fun isBrowser(): Boolean {
    return jsTypeOf(js("window")) != "undefined"
}
