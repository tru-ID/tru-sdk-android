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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.IOException


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal class Client(applicationContext: Context) {
    private val context = applicationContext
    private val client = OkHttpClient()

    init {
        val capabilities = intArrayOf(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val transportTypes = intArrayOf(NetworkCapabilities.TRANSPORT_CELLULAR)
        alwaysPreferNetworksWith(capabilities, transportTypes)
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
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerNetworkCallback(request.build(), object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                try {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        ConnectivityManager.setProcessDefaultNetwork(network)
                    } else {
                        connectivityManager.bindProcessToNetwork(network)
                    }
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "ConnectivityManager.NetworkCallback.onAvailable: ", e)
                }
            }
            override fun onLost(network: Network) {
                super.onLost(network)
                Log.d(TAG, "onLost: $network")
            }
            override fun onUnavailable() {
                super.onUnavailable()
                Log.d(TAG, "onUnavailable")
            }
        })
    }

    /**
     * Request the @param url on the mobile device over the mobile data connection.
     *
     * @return The response as a string. By default, the response bytes are decoded as UTF-8.
     *
     * @throws IOException if the request could not be executed due to cancellation, a connectivity
     *     problem or timeout. Because networks can fail during an exchange, it is possible that the
     *     remote server accepted the request before the failure.
     * @throws IllegalStateException when the call has already been executed.
     */
    @Throws(java.io.IOException::class)
    fun requestSync(@NonNull url: String, @NonNull method: String, @Nullable body: RequestBody? = null): String {
        val request = Request.Builder()
            .method(method, body)
            .url(url)
            .addHeader(HEADER_USER_AGENT, SDK_USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")

            val rawResponse = response.body!!.string()
            Log.d(TAG, "Response to $url")
            Log.d(TAG, rawResponse)
            return rawResponse
        }
    }
    companion object {
        private const val TAG = "Client"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val SDK_USER_AGENT = "tru_sdk_android"
    }
}
