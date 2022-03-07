
# tru-sdk-android

[![License][license-image]][license-url]


Installation
-------------

Add our maven public repository to your IDE

```
https://gitlab.com/api/v4/projects/22035475/packages/maven 
```

build.gradle -> dependencies add

```
    implementation 'id.tru.sdk:tru-sdk-android:0.3.1'
    implementation 'commons-io:commons-io:2.4'
```

Compatibility
-------------

 * **Minimum Android SDK**: TruSDK requires a minimum API level of 21 (Android 5)
 * **Compile Android SDK**: TruSDK requires you to compile against API 30  (Android 11) or later.

Usage example
-------------

```
import id.tru.sdk.TruSDK

TruSDK.initializeSdk(this.applicationContext)
...
val truSdk = TruSDK.getInstance()
...

val response = truSdk.checkUrlWithResponseBody(checkUrl)

val reachabilityDetails = truSdk.isReachable()

```


Build & Publish
-------------

Make sure the env var `ANDROID_SDK_ROOT` is defined (i.e `export ANDROID_SDK_ROOT=~/Library/Android/sdk`)

Build the AAR
```
./gradlew assemble
```

Publish to Package Repository (Internal Use Only)

Requires a `Deploy-Token` with scope `read_package_registry` & `write_package_registry`

Add the newly created token to the env var `TRU_SDK_ANDROID_TOKEN`

Update server in build.gradle

```
./gradlew publish
```

Meta
-------------
Distributed under the MIT license. See ``LICENSE`` for more information.

[https://github.com/tru-ID](https://github.com/tru-ID)

[license-image]: https://img.shields.io/badge/License-MIT-blue.svg
[license-url]: LICENSE
