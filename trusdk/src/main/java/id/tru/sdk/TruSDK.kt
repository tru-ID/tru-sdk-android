/*
 * MIT License
 * Copyright (C) 2020 4Auth Limited. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package id.tru.sdk

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import id.tru.sdk.network.CellularNetworkManager
import id.tru.sdk.network.NetworkManager
import id.tru.sdk.network.TraceInfo
import java.io.IOException
import java.net.URL
import org.json.JSONObject

/**
 * TruSDK main entry point.
 *
 * Usage example
 * ```
 * TruSDK.initializeSdk(requireContext())
 * private val truSdk = TruSDK.getInstance()
 *
 * truSdk.check(checkUrl)
 * truSdk.isReachable()
 * ```
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class TruSDK private constructor(context: Context) {
    private val context = context

    /**
     * Execute a phone check verification, by performing a network request against the Mobile carrier
     * over mobile data connection.
     *
     * Invokes the request immediately, and blocks until the response can be processed or is in error.
     *
     * Prerequisites:
     * Get the mobile application user's phone number and create a PhoneCheck via the tru.ID API
     * in order to receive a unique `check_url` in the response.
     * Request the `check_url` on the mobile device over the mobile data connection.
     *
     * @param checkUrl The phone check url.
     *
     * @WorkerThread
     * @throws IOException if the request could not be executed due to cancellation, a connectivity
     *     problem or timeout. Because networks can fail during an exchange, it is possible that the
     *     remote server accepted the request before the failure.
     * @throws IllegalStateException when the call has already been executed.
     * @return Indicating whether the request was made on a Cellular Network or not
     */
    @Throws(java.io.IOException::class)
    @Deprecated("Use check(checkUrl)", replaceWith = ReplaceWith("check(checkUrl)"))
    suspend fun openCheckUrl(@NonNull checkUrl: String): Boolean {
        Log.d("TruSDK", "openCheckURL")
        val networkManager: NetworkManager = getCellularNetworkManager()
        return networkManager.check(url = URL(checkUrl))
    }
    /**
     * Execute a phone check verification, by performing a network request against the Mobile carrier
     * over mobile data connection.
     *
     * Invokes the request immediately, and blocks until the response can be processed or is in error.
     *
     * Prerequisites:
     * Get the mobile application user's phone number and create a PhoneCheck via the tru.ID API
     * in order to receive a unique `check_url` in the response.
     * Request the `check_url` on the mobile device over the mobile data connection.
     *
     * @param checkUrl The phone check url.
     *
     * @WorkerThread
     * @throws IOException if the request could not be executed due to cancellation, a connectivity
     *     problem or timeout. Because networks can fail during an exchange, it is possible that the
     *     remote server accepted the request before the failure.
     * @throws IllegalStateException when the call has already been executed.
     * @return Indicating whether the request was made on a Cellular Network or not
     */
    @Throws(java.io.IOException::class)
    suspend fun check(@NonNull checkUrl: String): Boolean {
        Log.d("TruSDK", "openCheckURL")
        val networkManager: NetworkManager = getCellularNetworkManager()
        return networkManager.check(url = URL(checkUrl))
    }

    /**
     * Execute a phone check verification, by performing a network request against the Mobile carrier
     * over mobile data connection with a trace information.
     *
     * Invokes the request immediately, and blocks until the response can be processed or is in error.
     *
     * Prerequisites:
     * Get the mobile application user's phone number and create a PhoneCheck via the tru.ID API
     * in order to receive a unique `check_url` in the response.
     * Request the `check_url` on the mobile device over the mobile data connection.
     *
     * @param checkUrl The phone check url.
     *
     * @WorkerThread
     * @throws IOException if the request could not be executed due to cancellation, a connectivity
     *     problem or timeout. Because networks can fail during an exchange, it is possible that the
     *     remote server accepted the request before the failure.
     * @throws IllegalStateException when the call has already been executed.
     * @return A Pair. First value indicating whether the request was made on a Cellular Network or
     * not. Second value is the socket trace (request & response) as a String.
     */
    @Throws(java.io.IOException::class)
    suspend fun checkWithTrace(url: URL): TraceInfo {
        Log.d("TruSDK", "openCheckURL")
        val networkManager: NetworkManager = getCellularNetworkManager()
        return networkManager.checkWithTrace(url = url)
    }

    /**
     * Executes a network call to find out about the details of the network, mobile carrier etc. in order to determine
     * if tru.ID has reachability for the network.
     *
     * @return [ReachabilityDetails] which may contain the details of the mobile carrier or an error
     * describing the issue.
     */
    fun isReachable(): ReachabilityDetails? {
        Log.d(TAG, "ReachabilityDetails for endpoint:${BuildConfig.TRU_ID_DEVICE_ID_SERVER_URL}")
        val networkManager: NetworkManager = getCellularNetworkManager()
        val json: JSONObject? = networkManager.getJSON(url = URL(BuildConfig.TRU_ID_DEVICE_ID_SERVER_URL))
        var reachabilityDetails: ReachabilityDetails? = null
        Log.d("TruSDK", "isReachable: $json")
        if (json != null) {
            if (json.has("network_id")) {
                // We have reachability details
                val country = if (json.has("country_code")) { json.getString("country_code") } else { "" }
                val networkId = if (json.has("network_id")) { json.getString("network_id") } else { "" }
                val networkName = if (json.has("network_name")) { json.getString("network_name") } else { "" }
                val links = if (json.has("_links")) { json.getString("_links") } else { null }
                val products: ArrayList<Product>? = if (json.has("products")) {
                    val array = json.getJSONArray("products")
                    var _prod: ArrayList<Product> = ArrayList<Product>()
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val productName = item.getString("product_name")
                        val productId = item.getString("product_id")
                        _prod.add(Product(productId, productName))
                    }
                    _prod
                } else { null }
                    reachabilityDetails = ReachabilityDetails(null, country, networkId, networkName, products, links)
            } else {
                val title = if (json.has("title")) {
                    json.getString("title")
                } else {
                    null
                }
                val type = if (json.has("type")) {
                    json.getString("type")
                } else {
                    null
                }
                val status = if (json.has("status")) {
                    json.getInt("status")
                } else {
                    0
                }
                val detail = if (json.has("detail")) {
                    json.getString("detail")
                } else {
                    null
                }
                var error = ReachabilityError(type, title, status, detail)

                reachabilityDetails = ReachabilityDetails(error, "", "", "", null, null)
            }
        }
        return reachabilityDetails
    }

    /**
     * Execute a network call to a specified [endpoint].
     * Invokes the GET request immediately, and blocks until the response can be processed or is in error.
     *
     * Example usage: Read the IP address of the device over the mobile connection, in order to determine
     * if tru.ID has reachability for the network.
     *
     * @WorkerThread
     * @throws IOException if the request could not be executed due to cancellation, a connectivity
     *     problem or timeout. Because networks can fail during an exchange, it is possible that the
     *     remote server accepted the request before the failure.
     * @throws IllegalStateException when the call has already been executed.
     * @return The response as a JSONObject, or null if response cannot be processed.
     */
    @Throws(java.io.IOException::class)
    @Deprecated("Use isReachable()", replaceWith = ReplaceWith("isReachable()"))
    fun getJsonResponse(@NonNull endpoint: String): JSONObject? {
        Log.d(TAG, "getJsonResponse for endpoint:$endpoint")
        val networkManager: NetworkManager = getCellularNetworkManager()
        return networkManager.getJSON(url = URL(endpoint))
    }

    /**
     * Executes a network call to a specified [endpoint].
     * Invokes the GET request immediately, and blocks until the response can be processed or is in error.
     *
     * Example usage: Read the IP address of the device over the mobile connection, in order to determine
     * if tru.ID has reachability for the network.
     *
     * @WorkerThread
     * @throws IOException if the request could not be executed due to cancellation, a connectivity
     *     problem or timeout. Because networks can fail during an exchange, it is possible that the
     *     remote server accepted the request before the failure.
     * @throws IllegalStateException when the call has already been executed.
     * @return The value mapped by [key] if it exists, coercing it if necessary, or the empty string
     * if no such mapping exists.
     */
    @Throws(java.io.IOException::class)
    @Deprecated("Use isReachable()")
    fun getJsonPropertyValue(@NonNull endpoint: String, @NonNull key: String): String? {
        Log.d(TAG, "getJsonPropertyValue for endpoint:$endpoint key:$key")
        val networkManager: NetworkManager = getCellularNetworkManager()
        val json: JSONObject? = networkManager.getJSON(url = URL(endpoint))
        return json?.optString(key)
    }

    private fun getCellularNetworkManager(): NetworkManager {
        return CellularNetworkManager(context)
    }

    companion object {
        private const val TAG = "TruSDK"
        private var instance: TruSDK? = null

        @Synchronized
        fun initializeSdk(context: Context): TruSDK {
            var currentInstance = instance
            if (null == currentInstance) {
                currentInstance = TruSDK(context)
            }
            instance = currentInstance
            return currentInstance
        }

        @Synchronized
        fun getInstance(): TruSDK {
            val currentInstance = instance
            checkNotNull(currentInstance) {
                TruSDK::class.java.simpleName +
                        " is not initialized, call initializeSdk(...) first"
            }
            return currentInstance
        }
    }
}
