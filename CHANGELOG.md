
# tru.ID SDK for Android

Change Log
==========
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
