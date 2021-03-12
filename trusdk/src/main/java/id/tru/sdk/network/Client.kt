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
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi
import id.tru.sdk.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.IOException
import org.json.JSONException
import org.json.JSONObject


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal class Client(applicationContext: Context) {
    private val context = applicationContext
    private val client = OkHttpClient()
    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
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
    fun requestSync(@NonNull url: String, @NonNull method: String, @Nullable body: RequestBody? = null): JSONObject? {

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

    private fun alwaysPreferNetworksWith(
        capabilities: IntArray,
        transportTypes: IntArray
    ) {
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

    private fun executeRequest(@NonNull url: String, @NonNull method: String, @Nullable body: RequestBody? = null): String? {
        val request = Request.Builder()
            .method(method, body)
            .url(url)
            .addHeader(HEADER_USER_AGENT,
                SDK_USER_AGENT + "/" + BuildConfig.VERSION_NAME + " " +
                       "Android" + "/" + Build.VERSION.RELEASE)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")

            return response.body?.string()
        }
    }

    companion object {
        private const val TAG = "Client"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val SDK_USER_AGENT = "tru-sdk-android"
    }
}
