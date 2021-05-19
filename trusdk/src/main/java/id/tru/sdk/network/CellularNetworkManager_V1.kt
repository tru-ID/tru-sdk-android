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
import okhttp3.RequestBody
import org.json.JSONObject
import java.net.URL

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal class CellularNetworkManager_V1(context: Context): CellularNetworkManager {
    private val context = context

    private val connectivityManager by lazy {
        this.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private var networkCallback:  ConnectivityManager.NetworkCallback? = null

    /**
     * Request the @param url on the mobile device over the mobile data connection.
     *
     * @throws IOException if the request could not be executed due to cancellation, a connectivity
     *     problem or timeout. Because networks can fail during an exchange, it is possible that the
     *     remote server accepted the request before the failure.
     * @throws IllegalStateException when the call has already been executed.
     */
    @Throws(java.io.IOException::class)
    override fun call(@NonNull url: URL) {
        Log.d(TAG, "Triggering open check url")

        val capabilities = intArrayOf(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val transportTypes = intArrayOf(NetworkCapabilities.TRANSPORT_CELLULAR)

        configureNetworkWithPreferred(capabilities, transportTypes)

        var cs = ClientSocket()
        cs.check(url)
        // Release the request when done.
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
    }

    private fun configureNetworkWithPreferred(capabilities: IntArray, transportTypes: IntArray) {
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

    companion object {
        private const val TAG = "CellularNM_V1"
    }

}