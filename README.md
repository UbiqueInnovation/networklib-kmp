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

_⚠ under construction ⚠_

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
