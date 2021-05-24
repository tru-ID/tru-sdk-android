package id.tru.sdk.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import id.tru.sdk.BuildConfig
import java.net.URL
import java.util.TimerTask
import java.util.Timer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.schedule
import kotlin.concurrent.withLock

@RequiresApi(Build.VERSION_CODES.LOLLIPOP) // API 21, Android 5.0
internal class CellularNetworkManager_V1(context: Context) : CellularNetworkManager {
    private val context = context

    private val connectivityManager by lazy {
        this.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private var timeoutTask: TimerTask? = null

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Request the @param url on the mobile device over the mobile data connection.
     * @param url to be requested
     * @return A true if the request was successfully made on a cellular network, otherwise false
     */
    override fun call(@NonNull url: URL): Boolean {
        var calledOnCellularNetwork = false
        Log.d(TAG, "Triggering open check url")
        val lock = ReentrantLock()
        val condition = lock.newCondition()

        val capabilities = intArrayOf(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val transportTypes = intArrayOf(NetworkCapabilities.TRANSPORT_CELLULAR)

        configureNetworkWithPreferred(capabilities, transportTypes) {

            lock.withLock {
                if (it) {
                    calledOnCellularNetwork = true
                }
                var cs = ClientSocket()
                cs.check(url)
                // Release the request when done.
                networkCallback?.let {
                    connectivityManager.unregisterNetworkCallback(it)
                }
                condition.signal()
            }
        }

        lock.withLock {
            condition.await()
        }
        return calledOnCellularNetwork
    }

    /**
     * Configures a network with the capabilities and transport types, registers it and when it is
     * available calls onCompletion lambda with value of true.
     * 20 secs timeout applies if the network never registers, then the lambda is called with
     * a value of false.
     */
    private fun configureNetworkWithPreferred(
        capabilities: IntArray,
        transportTypes: IntArray, onCompletion: (isSucess: Boolean) -> Unit
    ) {
        val request = NetworkRequest.Builder()
        //Just in case as per Documentation
        request.removeTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        request.removeTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH)

        for (capability in capabilities) {
            request.addCapability(capability)
        }
        for (transportType in transportTypes) {
            request.addTransportType(transportType)
        }

        networkCallback = object : ConnectivityManager.NetworkCallback() {
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
                cancelTimeout()
                onCompletion(true)
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
        Log.d(TAG, "Cellular requested")

        timeoutTask = Timer("SettingUp", true).schedule(TIME_OUT) {
            Log.d(TAG, "Timeout...")
            Thread(Runnable { onCompletion(false) }).start()
        }
        connectivityManager.registerNetworkCallback(
            request.build(),
            networkCallback as ConnectivityManager.NetworkCallback
        )
    }

    private fun cancelTimeout() {
        Log.d(TAG, "Cancelling timeout")
        timeoutTask?.let { it.cancel() }
    }

    companion object {
        private const val TAG = "CellularNM_V1"
        private const val TIME_OUT: Long = 20000 //20 secs, same as iOS
    }

}