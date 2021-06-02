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
package id.tru.sdk.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import id.tru.sdk.BuildConfig
import id.tru.sdk.Product
import id.tru.sdk.ProductType.*
import id.tru.sdk.ReachabilityDetails
import id.tru.sdk.ReachabilityError
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.IOException
import org.json.JSONException
import org.json.JSONObject


@RequiresApi(Build.VERSION_CODES.LOLLIPOP) //API level 21, Lollipop 5.0, 92% coverage
internal class HttpClient(context: Context) {
    private val context = context

    private val client by lazy { OkHttpClient() }

    private val connectivityManager by lazy {
        this.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }

    private var networkCallback:  ConnectivityManager.NetworkCallback? = null

    /**
     * Request the @param url on the mobile device over the mobile data connection.
     * Unless otherwise specified, response bytes are decoded as UTF-8.
     *
     * @return Optional JSONObject if the response contains a body.
     *
     * @throws IOException if the request could not be executed due to cancellation, a connectivity
     *     problem or timeout. Because networks can fail during an exchange, it is possible that the
     *     remote server accepted the request before the failure.
     * @throws IllegalStateException when the call has already been executed.
     */
    @Throws(java.io.IOException::class)
    @Deprecated("This method will be deprecated in a future release")
    fun requestSync(url: String, method: String, body: RequestBody? = null): JSONObject? {

        val capabilities = intArrayOf(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val transportTypes = intArrayOf(NetworkCapabilities.TRANSPORT_CELLULAR)

        alwaysPreferNetworksWith(capabilities, transportTypes)

        val response = executeRequest(url, method, body)

        // Release the request when done.
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }

        response?.let{
            try {
                return JSONObject(it)
            } catch (e: JSONException) {}
        }

        return null
    }

    @Deprecated("To be removed in the next release")
    private fun alwaysPreferNetworksWith(capabilities: IntArray, transportTypes: IntArray) {
        val request = NetworkRequest.Builder()

        for (capability in capabilities) {
            request.addCapability(capability)
        }
        for (transportType in transportTypes) {
            request.addTransportType(transportType)
        }

        networkCallback = object:  ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                try {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        ConnectivityManager.setProcessDefaultNetwork(network)
                    } else {
                        connectivityManager.bindProcessToNetwork(network)
                    }
                } catch (e: IllegalStateException) {
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, "ConnectivityManager.NetworkCallback.onAvailable: ", e)
                    }
                }
            }
            override fun onLost(network: Network) {
                super.onLost(network)
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "onLost: $network")
                }
            }
            override fun onUnavailable() {
                super.onUnavailable()
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "onUnavailable")
                }
            }
        }
        connectivityManager.registerNetworkCallback(request.build(), networkCallback as ConnectivityManager.NetworkCallback)
    }

    /**
     * Requests the @param url to find out the mobile Reachability status.
     * @param full url to the Tru.Id endpoint (/device_ip)
     * @param HTTP method - default is GET
     */
    fun requestReachability(url: String, method: String = "GET"): ReachabilityDetails? {

        var reachabilityDetails: ReachabilityDetails? = null

        executeRequest(url, method, null) { code, body ->

            body?.let{
                try {
                    val json = JSONObject(it)
                    when {
                        code < 300 -> {
                            //We have reachability details
                            val country = if (json.has("country_code")) { json.getString("country_code") } else { null }
                            val networkId = if (json.has("network_id")) { json.getString("network_id") } else { null }
                            val networkName = if (json.has("network_name")) { json.getString("network_name") } else { null }
                            val links = if (json.has("_links")) { json.getString("_links") } else { null }
                            val products: ArrayList<Product>? = if (json.has("products")) {
                                val array = json.getJSONArray("products")
                                var _prod: ArrayList<Product> = ArrayList<Product>()
                                for (i in 0 until array.length()) {
                                    val item = array.getJSONObject(i)
                                    val productName = item.getString("product_name")
                                    val productId = item.getString("product_id")
                                    var productType = when(productName) {
                                        PhoneCheck.text -> PhoneCheck
                                        SIMCheck.text -> SIMCheck
                                        SubscriberCheck.text -> SubscriberCheck
                                        else -> Unknown
                                    }
                                    _prod.add(Product(productId, productType))
                                }
                                _prod
                            } else { null }

                            reachabilityDetails = ReachabilityDetails(null, country, networkId, networkName, products, links)

                        }
                        code == 412 -> { //status code = 412
                            val title = if (json.has("title")) { json.getString("title") } else { null }
                            val type = if (json.has("type")) { json.getString("type") } else { null }
                            val status = if (json.has("status")) { json.getInt("status") } else { 0 }
                            val detail = if (json.has("detail")) { json.getString("detail") } else { null }
                            var error = ReachabilityError(type, title, status, detail)

                            reachabilityDetails = ReachabilityDetails(error,null,null, null, null, null)
                        }
                        else -> {
                            Log.d(TAG,"HTTP status code is unexpected ${code}" )
                        }
                    }
                } catch (e: JSONException) { Log.d(TAG,"JSON Exception") }
            }

        }

        return reachabilityDetails
    }

    private fun executeRequest(url: String,
                               method: String,
                               body: RequestBody? = null,
                               onCompletion: (status: Int, body: String?) -> Unit) {

        val request = Request.Builder()
            .method(method, body)
            .url(url)
            .addHeader(HEADER_USER_AGENT, userAgent())
            .build()

        client.newCall(request).execute().use { response ->
            onCompletion(response.code, response.body?.string())
        }
    }

    private fun executeRequest(url: String,
                               method: String,
                               body: RequestBody? = null): String? {

        val request = Request.Builder()
            .method(method, body)
            .url(url)
            .addHeader(HEADER_USER_AGENT, userAgent())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.d(TAG, "Response $response")
                Log.d(TAG, "Response ${response.body?.string()}")
                throw IOException("Unexpected code $response")
            }

            return response.body?.string()
        }
    }

    private fun userAgent(): String {
        return SDK_USER_AGENT + "/" + BuildConfig.VERSION_NAME + " " + "Android" + "/" + Build.VERSION.RELEASE
    }

    companion object {
        private const val TAG = "HTTPClient"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val SDK_USER_AGENT = "tru-sdk-android"
    }
}