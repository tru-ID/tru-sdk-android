package id.tru.sdk.network

import android.content.Context
import android.net.*
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.os.Build
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import java.net.URL

/**
 * SDK at the moment support API level 21, Lollipop 5.0, 92% coverage
 * TEST CASES to handle:
 * 1) Mobile Data / Roaming disabled, Wifi avaialble or not
 * 2) Airplane mode
 * 3) Don't Disturb
 * 4) Restricting apps to only use WiFi
*/
@RequiresApi(Build.VERSION_CODES.O) // API Level 26, Oreo, Android 8.0 (61%) coverage
internal class CellularNetworkManager constructor(context: Context)  {

    private var defaultNetworkCallBack: ConnectivityManager.NetworkCallback? = null
    private var cellularNetworkCallBack: ConnectivityManager.NetworkCallback? = null

    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val cellularInfo by lazy {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        CellularInfo(telephonyManager)
    }

    fun call(url: URL) {
        checkNetworks()
        /* Whether Cellular Network is Active Network OR (Available and Bound to the process)
        * isCellularActiveNetwork() || (isCellularAvailable() && isCellularBoundToProcess())
        * OR NOT
        * We are requesting cellular data network. If it it no disabled by the user or by network,
        * it should be available. A further optimasation can be done perhaps, with helper check methods.
        */
        forceCellular {
            if (it) {
                var isAvailable = isCellularAvailable()
                Log.d(TAG,"-> Async Network: After forcing isAvailable? $isAvailable")
                var isBound = isCellularBoundToProcess()
                Log.d(TAG,"-> Async Network: After forcing isBound? $isBound")
                // We have Mobile Data registered and bound for use
                // However, user may still have not data plan!
                //Sockets
                var cs = ClientSocket()
                cs.check(url)
                unregisterCellularNetworkListener()
                checkNetworks()
            } else {
                Log.d(TAG,"We do not have a path")
            }
        }
    }

    private fun isDataEnabled():Boolean { return cellularInfo.isDataEnabled() }

    /**
     * Requests cellular network. Even though the device may have mobile data, and enabled,
     * some Android devices may not show it on the available networks list. They tend to set WiFi
     * as the default and active network. This method requests the mobile data network to be
     * available, and when available it bind the network to the process to be used later.
     *
     */
    private fun forceCellular(onCompletion: (isSucess: Boolean) -> Unit) {
        Log.d(TAG, "------ Forcing Cellular ------")
        Log.d(TAG,"Checking if Mobile Data is enabled..")

        if (!isDataEnabled()) {
            Log.d(TAG,"-> Mobile Data is NOT enabled, we can not force cellular!")
            Thread(Runnable {
                Log.d(TAG,"Calling completion -- Is Main thread? ${isMainThread()}")
                onCompletion(false)
            }).start()
            return
        }

        Log.d(TAG,"-> Mobile Data is Enabled!")
        if (cellularNetworkCallBack == null) {

            cellularNetworkCallBack = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Cellular OnAvailable:")
                    //cancelTimeout()
                    networkInfo(network)
                    try {
                        //Binds the current process to network.  All Sockets created in the future
                        // (and not explicitly bound via a bound SocketFactory from {@link Network#getSocketFactory() Network.getSocketFactory()})
                        // will be bound to network.
                        Log.d(TAG, "  Binding to process:")
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {//23 Android 6.0 Marsh
                            ConnectivityManager.setProcessDefaultNetwork(network)
                        } else {
                            connectivityManager.bindProcessToNetwork(network) //API Level 23
                            //OR you can bind the socket to the Network
                            //network.bindSocket()
                        }
                        Log.d(TAG,"Binding finihsed. Is Main thread? ${isMainThread()}")
                        onCompletion(true)
                    } catch (e: IllegalStateException) {
                        Log.d(TAG, "ConnectivityManager.NetworkCallback.onAvailable: $e")
                    }
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "Cellular OnLost:")
                    networkInfo(network)
                }

                override fun onUnavailable() {
                    Log.d(TAG, "Cellular onUnavailable")
                    //When this method gets called due to timeout, the callback will automatically be unregistered
                    //So no need to call unregisterCellularNetworkListener()
                    //But we should null it
                    cellularNetworkCallBack = null
                    onCompletion(false)
                    super.onUnavailable()
                }
            }

            Log.d(TAG,"Creating a network builder on Main thread? ${isMainThread()}")
            val request = NetworkRequest.Builder()
            request.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            request.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {//API Level 26
                //The network request will live, until unregisterNetworkCallback is called or app exit.
                connectivityManager.requestNetwork(request.build(),
                    cellularNetworkCallBack as ConnectivityManager.NetworkCallback, 5000
                )
            }

            Log.d(TAG, "Forcing Cellular - Requesting to registered...")
        } else {
            Log.d(TAG, "There is already a Listener registered.")
            //Perhaps there is already one registered, and in progress or waiting to be timed out
        }

    }

    private fun unregisterCellularNetworkListener() {
        Log.d(TAG, "UnregisteringCellularNetworkListener")
        cellularNetworkCallBack?.let {
            Log.d(TAG, "CallBack available, unregistering it.")
            connectivityManager.unregisterNetworkCallback(cellularNetworkCallBack as ConnectivityManager.NetworkCallback)
            cellularNetworkCallBack = null
        }
    }

    private fun isMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }

    private fun boundNetwork() { //API 23
        Log.d(TAG, "----- Bound network ----")
        connectivityManager.boundNetworkForProcess?.let { networkInfo(it) }
    }

    private fun activeNetworkInfo() { //API 23
        Log.d(TAG, "---- Active network ----")
        connectivityManager.activeNetwork?.let { networkInfo(it) }
    }

    private fun isCellularActiveNetwork(): Boolean { //API 23
        connectivityManager.activeNetwork?.let {
            return isCellular(it)
        }
        return false
    }

    private fun isCellularBoundToProcess(): Boolean { //API 23
        connectivityManager.boundNetworkForProcess?.let {
            return isCellular(it)
        }
        return false
    }

    private fun isCellular(network: Network): Boolean {
        val caps = connectivityManager.getNetworkCapabilities(network)
        caps?.let {
            if (it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return true
            }
        }
        return false
    }

    private fun isCellularAvailable(): Boolean {
        val networks = connectivityManager.allNetworks
        var available = false
        for (network in networks) {
            available = isCellular(network)
            if (available) break
        }
        return available
    }

    private fun checkNetworks() {
        Log.d(TAG, "----- Check ------")
        Log.d(TAG, "Is Default Network Active? " + connectivityManager.isDefaultNetworkActive.toString())
        boundNetwork()
        activeNetworkInfo()
        availableNetworks()
    }

    @RequiresApi(api = Build.VERSION_CODES.R)//30
    private fun networkType(capability: NetworkCapabilities) {
        when (capability.networkSpecifier) {
            is TelephonyNetworkSpecifier -> Log.d(TAG, "Cellular network")
            is WifiNetworkSpecifier -> Log.d(TAG, "Wifi network")
            is WifiAwareNetworkSpecifier -> Log.d(TAG, "Wifi Aware network")
        }
    }

    private fun availableNetworks() {
        Log.d(TAG, "----------Available Networks----------")

        val networks = connectivityManager.allNetworks
        for (network in networks) {

            networkInfo(network)

            val caps = connectivityManager.getNetworkCapabilities(network)
            caps?.let {

                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) { // IF > API Level 29
                    networkType(it)
                    Log.d(TAG, "Signal Strength : " + it.signalStrength)

                    val transportInfo = it.transportInfo // API Level 29, WifiAwareNetworkInfo, WifiInfo
                    if (it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        Log.d(TAG, "Cap: Internet Capability")
                    }
                    if (it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        Log.d(TAG, "Cap: Cellular")
                    }
                    if (it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)) {
                        Log.d(TAG, "Cap: Wifi Aware")
                    }
                    if (it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        Log.d(TAG, "Cap: Wifi")
                    }
                    if (it.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                        Log.d(TAG, "Cap: Bluetooth")
                    }
                }

            }
        }
    }

    private fun networkInfo(network: Network) {
        Log.d(TAG, "Name:" + linkName(network))
        linkAddresses(network)
    }

    private fun linkName(network: Network): String {
        val activeNetworkLinkProperties = connectivityManager.getLinkProperties(network)
        return activeNetworkLinkProperties?.interfaceName ?: "None"
    }

    private fun linkAddresses(network: Network) {
        val activeNetworkLinkProperties = connectivityManager.getLinkProperties(network)

        activeNetworkLinkProperties?.linkAddresses?.let {
            for( address in it) {
                Log.d(TAG, "Address: ${address.toString()}")
            }
        }
    }

    // In order to listen to the Default network
    private fun registerDefaultNetworkListener() {
        //Registers to receive notifications about changes in the **application's default network**.
        defaultNetworkCallBack = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Default OnAvailable:")
                networkInfo(network)
            }
            override fun onLost(network: Network) {
                Log.d(TAG, "Default OnLost:")
                networkInfo(network)
            }

            override fun onUnavailable() {
                Log.d(TAG, "Default onUnavailable")
                super.onUnavailable()
            }
        }
        defaultNetworkCallBack?.let { connectivityManager.registerDefaultNetworkCallback(it) }
    }

    fun unregisterDefaultNetworkListener() {
        defaultNetworkCallBack?.let { connectivityManager.unregisterNetworkCallback(it) }
    }

    private fun backgroundConnectivityStatus() {
        //is subject to metered network restrictions while running on background?
        when(connectivityManager.restrictBackgroundStatus) {
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> Log.d(TAG,"Background restriction status: DISABLED")
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> Log.d(TAG,"Background restriction status: ENABLED")
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> Log.d(TAG,"Background restriction status: STATUS_WHITELISTED")
            else -> Log.d(TAG,"Background restriction status unknown")
        }
    }

    companion object {
        private const val TAG = "CellularNetworkManager"
    }
}