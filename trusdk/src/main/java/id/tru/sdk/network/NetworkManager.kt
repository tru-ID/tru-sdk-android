package id.tru.sdk.network

import okhttp3.RequestBody
import org.json.JSONObject
import java.net.URL

internal interface NetworkManager {
    fun check(url: URL): Boolean
    fun checkWithTrace(url: URL): TraceInfo
    fun requestSync(
        url: URL,
        method: String,
        body: RequestBody? = null
    ): JSONObject?

    fun requestSync(
        url: URL,
        method: String,
        headerName: String? = null,
        headerValue: String? = null,
        body: RequestBody? = null
    ): String?
}