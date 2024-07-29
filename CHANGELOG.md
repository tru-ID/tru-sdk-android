
# tru.ID SDK for Android

Change Log
## Version 1.0.10
_2023-07-24_
**Changes**
- compileOptions set to Java version 11 for apps using JDK 11 to ensure compatibility.

## Version 1.0.9
_2023-07-01_
**Changes**
- targetSdkVersion updated to 34
- kotlin_version updated to 1.8.0
- gradlePluginVersion updated to 8.4.2
- gradleVersion updated to 8.6
- JavaVersion updated to 17
- Previously deprecated `postWithDataCellular` method and relevant testing removed

## Version 1.0.8
_2023-01-24_
**Changes**
- `send` and `post` methods updated to prevent http call requests
- `post` method amended to prevent CRLF injections
- `postWithDataCellular` method now deprecated and will be removed in future releases

## Version 1.0.7
_2023-01-19_
**Bug Fix**
- `sendAndReceive` method updated

## Version 1.0.6
_2023-08-11_
**Bug Fix**
- `makeHTTPCommand` empty path handling

## Version 1.0.5
_2023-07-04_
**Changes**
- kotlin_version 1.5.20
- compileSdkVersion 33
- targetSdkVersion 33
**Bug Fix**
- HTTP status parsing
**New**
- Convenience method `postWithDataCellular`

## Version 1.0.4
_2023-04-23_
**New**
- Changes removing dependency on commons-io

## Version 1.0.3
_2023-01-23_
**New**
- New method `openWithDataCellularAndAccessToken`

## Version 1.0.2
_2022-10-31_

**Changes**
- targetSdkVersion increased to 31 (Android 12)

## Version 1.0.1
_2022-09-29_

**Changes**
- Bug fix related to MNO requiring cookies 

## Version 1.0.0
_2022-09-26_

**Changes**
- README 

## Version 1.0.0-preview
_2022-08-08_

**Changes**
- Breaking changes from 0.x.x, see README 

## Version 0.3.4
_2022-06-01_

**Changes**
- Improved internal exception handling

## Version 0.3.3
_2022-04-20_

**Bug Fix**
- "startConnection" error

## Version 0.3.2
_2022-03-27_

**Bug Fix**
- Redirect handling

## Version 0.3.1
_2022-03-07_

**New**
- Simulator supports

## Version 0.3.0
_2022-02-11_

**New**
- `checkUrlWithResponseBody` new method

**Changes**
- Method `check` is deprecated.
- Better network connection handling

## Version 0.2.11
_2022-01-11_

**Changes**
- Internal refactoring

## Version 0.2.10
_2021-11-15_

**Changes**
- Internal enhancement

## Version 0.2.9
_2021-11-12_

**New**
- `isReachable` support for custom data residency

## Version 0.2.8
_2021-10-07_

**Bug Fix**
- `ReachabilityDetails` result converted to snake_case

## Version 0.2.7
_2021-09-27_

**Bug Fix**
- `isReachable` method product mapping
- Better custom port handling
- Accept header for navigation request

## Version 0.2.6
_2021-09-06_

**Bug Fix**
- Fixed forcing data cellular connectivity in `isReachable` method.

## Version 0.2.5
_2021-07-27_

**Changes**
- Deprecated `openCheckUrl(url)` and replaced with `check(url)`

**New**
- Added a convenience method to serialise ReachabilityDetails to jsonString. ReachabilityDetails fields (countryCode, networkId, networkName) were made non-optional and initialised with empty string.

## Version 0.2.4
_2021-06-25_

**Bug Fix**
- Fixed a bug where SDK may not be able to make consecutive successful requests, after being in the background for a while or networks switches in the background between sessions 

## Version 0.2.3
_2021-06-23_

**Bug Fix**
- Fixed a bug where lowercasing for relative redirects is removed

## Version 0.2.2
_2021-06-23_

**Bug Fix**
- Fixed a bug where relative redirects where not handled.

## Version 0.2.1
_2021-06-14_

**New**
- Method `checkWithTrace` executes a phone check verification, by performing a network request against the Mobile carrier and produces a trace information which can be used for debugging problematic issues.

## Version 0.2.0
_2021-06-02_

**New**
- Method `isReachable` executes a GET request to a Tru.Id endpoint and returns the `ReachabilityDetails` object. You can inspect this object to find out about the Mobile Carrier details if the request was made over cellular network. Note that the this method doesn't necessarily force Android system to use a mobile network.

**Changes**
- Method `getJsonPropertyValue` is deprecated.
- Method `getJsonProperty` is deprecated.

## Version 0.1.0

_2021-05-24_

**Changes**
- Method `openCheckUrl` can now make network call over cellular network without needing the user to change the network type. The method has a return type indicating whether the call was made on a cellular network or not

## Version 0.0.3

_2021-04-20_

**Changes**
- Method `getJsonResponse` now has annotation for `@Throws` of `IOException` and `IllegalStateException`
- Method `getJsonPropertyValue` now has annotation for `@Throws` of `IOException` and `IllegalStateException`

## Version 0.0.2

_2021-03-15_

**New**
- Method `getJsonResponse` executes a GET request and returns the JSONObject response.
- Method `getJsonPropertyValue` executes a GET request and returns the value maped by a specific key.

**Changes**
- Method `openCheckUrl` now returns Void.
- Method `openCheckUrl` now specifies a HTTP USER-AGENT.


## Version 0.0.1

_2020-10-30_

**Release of tru-sdk-android**
