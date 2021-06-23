
# tru.ID SDK for Android

Change Log
==========
## Version 0.2.3
_2021-06-23_

**Bug Fix**
- Fixed a bug where lowecasing for relative redirects is removed

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
