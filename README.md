
# tru-sdk-android
[![License][license-image]][license-url]

The only purpose of the SDK is to force the data cellular connectivity prior to call a public URL, and will return the following JSON response

* **Success**
When the data connectivity has been achieved and a response has been received from the url endpoint
```
{
"http_status": string, // HTTP status related to the url
"response_body" : { // optional depending on the HTTP status
           ... // the response body of the opened url 
           ... // see API doc for /device_ip and /redirect
                },
"debug" : {
    "device_info": string, 
    "url_trace" : string
          }
}
```

* **Error** 
When data connectivity is not available and/or an internal SDK error occurred

```
{
"error" : string,
"error_description": string
}
```
Potential error codes: `sdk_no_data_connectivity`, `sdk_connection_error`, `sdk_redirect_error`, `sdk_error`.


## Installation

Add our maven public repository to your IDE

```
https://gitlab.com/api/v4/projects/22035475/packages/maven 
```

build.gradle -> dependencies add

```
    implementation 'id.tru.sdk:tru-sdk-android:x.y.z'
    implementation 'commons-io:commons-io:2.4'
```

## Compatibility


 * **Minimum Android SDK**: TruSDK requires a minimum API level of 21 (Android 5)
 * **Compile Android SDK**: TruSDK requires you to compile against API 30  (Android 11) or later.

 ## Size

 * **tru-sdk-android**: ~60KiB

## Usage example


```
import id.tru.sdk.TruSDK

// instantiate the sdk during app startup
TruSDK.initializeSdk(this.applicationContext)

val resp: JSONObject = TruSDK.getInstance().openWithDataCellular(URL(endpoint), false)
 if (resp.optString("error") != "") {
    // error
} else {
    val status = resp.optInt("http_status")
    if (status == 200) {
        // 200 OK
    } else {
        // error
    }
}
```

* Is the device eligible for tru.ID silent authentication?
```
    TruSDK.initializeSdk(this.applicationContext)
    val resp: JSONObject = TruSDK.getInstance().openWithDataCellular(URL("https://eu.api.tru.id/public/coverage/v0.1/device_ip"), false)
    if (resp.optString("error") != "") {
        println("not reachable: ${resp.optString("error_description","No error description found")}")
    } else {
        val status = resp.optInt("http_status")
        if (status == 200) {
            print("is reachable")
            val body = resp.optJSONObject("response_body")
            if (body != null)
                println("on " + body.optString("network_name"))
        } else if (status == 400) {
            println("not reachable: not a supported MNO")
        } else if (status == 412) {
            println("not reachable: not a mobile IP")
        } else {
            println("not reachable: other error")   
        }            
    }
```

* How to open a check URL return by the [PhoneCheck API](https://developer.tru.id/docs/phone-check) or [SubscriberCheck API](https://developer.tru.id/docs/subscriber-check)
```
    val resp: JSONObject? = TruSDK.getInstance().openWithDataCellular(URL(checkUrl), false)
    if (resp.optString("error") != "") {
        println("Error: ${resp.optString("error_description","No error description found")}")
    } else {
        val status = resp.optInt("http_status")
        if (status == 200) {
            val body = resp.optJSONObject("response_body")
            if (body != null) {
                val code = body.optString("code")
                if (code != null) {
                    val checkId = body.optString("check_id")
                    val ref = body.optString("check_id")
                    // send code, checkId and ref to back-end 
                    // to trigger a PATCH /checks/{check_id}
                } else {
                    val error = body.optString("error")
                    val desc = body.optString("error_description")
                    // error
                }
            } else {
                // invalid response format
            }
        } else if (status == 400) {
            // MNO not supported
        } else if (status == 412) {
            // MNO a mobile IP
        } else {
            // error
        }
    }

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

Update server in build.gradle

```
./gradlew publish
```

## Meta

Distributed under the MIT license. See ``LICENSE`` for more information.

[https://github.com/tru-ID](https://github.com/tru-ID)

[license-image]: https://img.shields.io/badge/License-MIT-blue.svg
[license-url]: LICENSE
