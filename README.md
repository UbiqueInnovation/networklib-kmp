# Networking for Kotlin Multiplatform

A networking library building on Ktor.

[![Build](https://github.com/UbiqueInnovation/networklib-kmp/actions/workflows/build.yml/badge.svg)](https://github.com/UbiqueInnovation/networklib-kmp/actions/workflows/build.yml)
[![Test](https://github.com/UbiqueInnovation/networklib-kmp/actions/workflows/test.yml/badge.svg)](https://github.com/UbiqueInnovation/networklib-kmp/actions/workflows/test.yml)

## Dependency

Available on Ubique's internal Artifactory:
```kotlin
implementation("ch.ubique.kmp:network:1.0.0")
```

You may find the current version and version history in the [Releases list](https://github.com/UbiqueInnovation/networklib-kmp/releases).

## Features

### Ubiquache Plugin
An application-level plugin for Ktor implementing a disk cache, supporting the major HTTP caching mechanisms as well as the custom cache rules specified by Ubique.

_⚠ under construction ⚠_

#### Usage
```kotlin
import ch.ubique.libs.ktor.plugins.Ubiquache

val client = HttpClient() {
   install(Ubiquache)
}
```

#### Functionality
Disk-level cache supporting the cache control mechanisms as defined by following HTTP headers:

Request:

* `Cache-Control: no-cache` – The response will not be loaded from cache and forces a network request.
* `Cache-Control: no-store` – The response will not be stored to cache, but may return a stored response from cache if it's valid.
* `Cache-Control: only-if-cached` – Prevent a network request. Fails with status code 504 if there is no valid cached response.

Response:

* `Expires: <date>`
* `X-Best-Before: <date>` – and variants; synonymous with `Expires`.
* `X-Next-Refresh: <date>` – and variants
* `ETag: <tag>`, `Last-Modified: <date>`
* `Cache-Control: max-age=<seconds>`
* `Cache-Control: no-cache`
* `Cache-Control: no-store`

A request is uniquely identified by the following attributes. If any of these values differ, the request is handled and cached separately.

* URL
* HTTP method
* Any Accept-\* headers
* Authorization header

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

#### Dynamic Request Parameter

Example of a `ktorStateFlow` with a changing request parameter, e.g. a filter.
Setting the field `exampleFilter` automatically forces a reload with the new value:

```kotlin
var exampleFilter: String = "default"
    set(value) {
        field = value
        stateflow.reload()
    }
val stateflow = ktorStateFlow<MyModel> { cacheControl ->
    client.get(url) {
        url { parameter("filter", exampleFilter) }
        cacheControl(cacheControl) 
    }
}
```

Or using a StateFlow as a value source instead:

```kotlin
val exampleFilter = MutableStateFlow("default")
val requestStateFlow = exampleFilter.flatMapLatest { filter ->
    ktorStateFlow<MyModel> { cacheControl ->
        client.get(url) {
            url { parameter("filter", filter) }
            cacheControl(cacheControl)
        }
    }
}
```

Example of a method returning a new `ktorStateFlow` instance for different but constant parameter values:

```kotlin
fun stateflow(exampleId: String) = ktorStateFlow<MyModel> { cacheControl ->
    client.get(url) {
        url { parameter("exampleId", exampleId) }
        cacheControl(cacheControl)
    }
}
```

### AcceptLanguage Plugin
HTTP client plugin to add the Accept-Language HTTP header. Either with a fixed language code, or a system dependent language list.

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

## Deployment

Each release on Github will be deployed to Ubique's internal Artifactory.

Use the `VERSION_NAME` as defined in the `gradle.properties` file as the release tag, prefixed with a `v`.

* Group: `ch.ubique.kmp`
* Artifact: `network`
* Version: `major.minor.revision`
