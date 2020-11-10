
# tru-sdk-android

[![License][license-image]][license-url]


## Installation

Add our maven public repository to your IDE

```
https://gitlab.com/api/v4/projects/22035475/packages/maven 
```

build.gradle -> dependencies add

```
    implementation 'id.tru.sdk:tru-sdk-android:0.0.1'
```

## Usage example

```
import id.tru.sdk.TruSDK

private val truSdk = TruSDK.getInstance()
truSdk.openCheckUrl(checkUrl)
```


## Build & Publish

Make sure the env var `ANDROID_SDK_ROOT` is defined (i.e `export ANDROID_SDK_ROOT=~/Library/Android/sdk`)

Build the AAR
```
 ./gradlew assemble
```

Publish to Package Repository (Internal Use Only)

Requires a `Deploy-Token` with scope `read_package_registry` & `write_package_registry`

Add the newly created token to the env var `TRU_SDK_ANDROID_TOKEN`

Update semver in build.gradle

```
 ./gradlew publish
```

## Meta

Distributed under the MIT license. See ``LICENSE`` for more information.

[https://github.com/tru-ID](https://github.com/tru-ID)

[license-image]: https://img.shields.io/badge/License-MIT-blue.svg
[license-url]: LICENSE