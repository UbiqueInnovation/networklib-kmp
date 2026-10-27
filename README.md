# Networking for Kotlin Multiplatform

[![Build](https://github.com/UbiqueInnovation/networklib-kmp/actions/workflows/build.yml/badge.svg)](https://github.com/UbiqueInnovation/networklib-kmp/actions/workflows/build.yml)
[![Test](https://github.com/UbiqueInnovation/networklib-kmp/actions/workflows/test.yml/badge.svg)](https://github.com/UbiqueInnovation/networklib-kmp/actions/workflows/test.yml)
[![Release](https://github.com/UbiqueInnovation/networklib-kmp/actions/workflows/release.yml/badge.svg)](https://github.com/UbiqueInnovation/networklib-kmp/actions/workflows/release.yml)
[![Maven Central](https://img.shields.io/maven-central/v/ch.ubique.kmp/network.svg?label=Maven%20Central)](https://search.maven.org/artifact/ch.ubique.kmp/network)

## Dependency

Available on Maven Central:
```kotlin
implementation("ch.ubique.kmp:network:1.1.1")
```

You may find the current version and version history in the [Releases list](https://github.com/UbiqueInnovation/networklib-kmp/releases).

### Ktor

This networking library builds on Ktor.

On how to use Ktor, have a look at the following resources:  
- [Creating a cross-platform mobile application using Ktor](https://ktor.io/docs/client-create-multiplatform-application.html)
- [Creating and configuring a client](https://ktor.io/docs/client-create-and-configure.html) 
- [Making requests with Ktor](https://ktor.io/docs/client-requests.html)

## Features

### Ubiquache Plugin
An application-level plugin for Ktor implementing a disk cache, supporting the major HTTP caching mechanisms as well as the custom cache rules specified by Ubique.

#### Usage
```kotlin
import ch.ubique.libs.ktor.plugins.Ubiquache

val client = HttpClient() {
    install(Ubiquache)
}
```

On Android, the Ubiquache plugin needs to be initialized before installing it in the Ktor client, e.g. in Application.onCreate():

```kotlin
UbiquacheConfig.init(context)
```

If you need multiple independent caches, you can configure each plugin instance with a distinct name. Furthermore, you can set the maximum cache size in bytes:

<details>
<summary>Code example</summary>

```kotlin
val client = HttpClient() {
    install(Ubiquache) {
        name = "my-cache"
        maxSize = 256 * 1024 * 1024 // 256 MB
    }
}
```

</details>

#### Cache Control
The disk-level cache supports the cache control mechanisms as defined by following HTTP headers:

<details>
<summary>Request HTTP headers</summary>

* `Cache-Control: no-cache` – The response will not be loaded from cache and forces a network request.
* `Cache-Control: no-store` – The response will not be stored to cache, but may return a stored response from cache if it's valid.
* `Cache-Control: only-if-cached` – Prevent a network request. Fails with status code 504 if there is no valid cached response.

</details>

<details>
<summary>Response HTTP headers</summary>

* `Expires: <date>`
* `X-Best-Before: <date>` – and variants; synonymous with `Expires`.
* `X-Next-Refresh: <date>` – and variants
* `ETag: <tag>`, `Last-Modified: <date>`
* `Cache-Control: max-age=<seconds>`
* `Cache-Control: no-cache`
* `Cache-Control: no-store`

</details>

A request is uniquely identified by the following attributes. If any of these values differ, the request is handled and cached separately.

* URL
* HTTP method
* Any Accept-\* headers
* Authorization header

#### Cache Status

Responses served or stored by Ubiquache include an RFC 9211 [`Cache-Status`](https://www.rfc-editor.org/rfc/rfc9211) response header. 
Ubiquache appends its structured-field member without replacing status entries from upstream caches.

* `"ubiquache"; hit` – The response was served directly from Ubiquache.
* `"ubiquache"; fwd=miss; stored` – No usable cached response existed; the response was fetched and stored.

```kotlin
val response = client.get(url)
val wasCacheHit = response.isCacheHit()
val wasReadFromCache = response.isReadFromCache()
```

`isCacheHit()` is true only when Ubiquache served the response without forwarding. `isReadFromCache()` also covers responses 
reconstructed after validation (e.g. status 304) and responses served as a fallback after a failed refresh.

#### Cache Management

By obtaining the plugin instance:

```kotlin
val ubiquache = httpClient.plugin(Ubiquache)
```

you can access basic cache information and perform cleanup operations:

* `ubiquache.clearCache()` – Removes all cached responses.
* `ubiquache.clearCache(url)` – Removes the cached response for a specific URL.
* `ubiquache.usedCacheSize()` – Current cache size in bytes.
* `ubiquache.maxCacheSize()` – Maximum cache size in bytes.

### Ktor StateFlow
`ktorStateFlow()` creates a `StateFlow` that, if it has active observers, executes a request and automatically refreshes
according to the response cache headers, i.e. reloads if the response needs to be refreshed or is expired.

This requires the Ktor request to forward a `Cache-Control` header and return a `Response` with the desired result type:

```kotlin
val stateflow = ktorStateFlow<MyModel> { cacheControl ->
    client.get(url) { cacheControl(cacheControl) }
}
```

The `StateFlow` holds the current state which is either `Loading`, `Result` containing the data, or `Error` with an exception and a retry function.

```kotlin
stateflow.collect { state ->
    when (state) {
        is RequestState.Loading -> { } // loading state
        is RequestState.Result -> { state.data } // result state
        is RequestState.Error -> { state.exception; state.retry() } // error state
    }
}
```

In case of an error, the `ktorStateFlow` stops and has to be restarted manually, either with `errorState.retry()` or with `stateflow.forceReload()`.

Use `asStateFlow()` to expose only the standard `StateFlow` API, keeping reload controls on the original instance:

```kotlin
import ch.ubique.libs.ktor.flow.asStateFlow

val readOnlyStateFlow = stateflow.asStateFlow()
```

#### Transforming Responses
Use the `resultTransform` overload to build a custom result model from a successful `HttpResponse`, including its headers.

```kotlin
data class ApiResult(
    val model: MyModel,
    val eTag: String?,
)

val stateFlow = ktorStateFlow(
    resultTransform = { response ->
        ApiResult(
            model = response.body(),
            eTag = response.headers[HttpHeaders.ETag],
        )
    },
) { cacheControl ->
    client.get(url) { cacheControl(cacheControl) }
}
```

#### Dynamic Request Parameter

Example of a `ktorStateFlow` with a changing request parameter, e.g. a filter.
Setting the field `exampleFilter` automatically forces a reload with the new value:

<details>
<summary>Code example</summary>

```kotlin
var exampleFilter: String = "default"
    set(value) {
        field = value
        stateflow.reload()
    }
val stateflow = ktorStateFlow<summary> { cacheControl ->
    client.get(url) {
        url { parameter("filter", exampleFilter) }
        cacheControl(cacheControl) 
    }
}
```

</details>

Or using a StateFlow as a value source instead, with `flatMapLatestToKtorStateFlow()`:

<details>
<summary>Code example</summary>

```kotlin
val exampleFilter = MutableStateFlow("default")
val requestStateFlow = exampleFilter.flatMapLatestToKtorStateFlow { filter ->
    ktorStateFlow<MyModel> { cacheControl ->
        client.get(url) {
            url { parameter("filter", filter) }
            cacheControl(cacheControl)
        }
    }
}
```

</details>

Example of a method returning a new `ktorStateFlow` instance for different but constant parameter values:

<details>
<summary>Code example</summary>

```kotlin
fun stateflow(exampleId: String) = ktorStateFlow<MyModel> { cacheControl ->
    client.get(url) {
        url { parameter("exampleId", exampleId) }
        cacheControl(cacheControl)
    }
}
```

</details>

### AcceptLanguage Plugin
HTTP client plugin to add the Accept-Language HTTP header. Either with a fixed language code, or a system dependent language list.

```kotlin
val client = HttpClient() {
   install(AcceptLanguage) {
      language = "de" // static ...
      languageProvider = { "de" } // ... or callback
   }
}
```

### AppUserAgent Plugin
HTTP client plugin to add the User-Agent HTTP header, containing basic system and app information.

```kotlin
val client = HttpClient() {
    AppUserAgent()
}
```

---

## Development & Testing

Most features of this library can be implemented with test-driven development using unit tests with a mock webserver instance.

To test any changes locally in an app, you can either include the library via dependency substitution in an application project,
or deploy a build to your local maven repository and include that from any application:

1. Define a unique custom version by setting the `VERSION_NAME` variable in the `gradle.properties` file.
2. Deploy the library artifact by running `./gradlew publishToMavenLocal`
3. Reference the local maven repository in your application's build script:

    ```kotlin
    repositories {
        mavenLocal()
    }
    ```

4. And apply the local library version:

    ```kotlin
    dependencies {
        implementation("ch.ubique.kmp:network:$yourLocalVersion")
    }
    ```

### Unit Tests

Unit tests and coverage reports are run on the JVM target by default. 
See also workflows for [Test](https://github.com/UbiqueInnovation/networklib-kmp/actions/workflows/test.yml) 
and [Coverage](https://github.com/UbiqueInnovation/networklib-kmp/actions/workflows/coverage.yml).

## Deployment

Create a [Release](https://github.com/UbiqueInnovation/networklib-kmp/releases),
setting the Tag to the desired version prefixed with a `v`.

Each release on Github will be deployed to Maven Central.

* Group: `ch.ubique.kmp`
* Artifact: `network`
* Version: `major.minor.revision`
