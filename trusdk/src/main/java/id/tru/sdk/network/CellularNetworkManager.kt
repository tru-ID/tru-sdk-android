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
import android.net.TelephonyNetworkSpecifier
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.os.Build
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import java.net.URL
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.schedule
import kotlin.concurrent.withLock
import org.json.JSONObject

/**
 * CellularNetworkManager requests Cellular Network from the system to be available to
 * current process. On some devices (such as Samsung and Huawei), when WiFi is on
 * it is possible that the devices default to the WiFi and set it as the active, and hide
 * the cellular (despite being available). This class (for API Level 26+) forces the system to
 * make the cellular network visible to the process.
 *
 * SDK at the moment support API level 21, Lollipop 5.0, 92% coverage
 * The following test cases are handled:
 * 1) Mobile Data / Roaming disabled, Wifi available or not
 * 2) Airplane mode
 * 3) Don't Disturb
 * 4) Restricting apps to only use WiFi
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP) // API 21, Android 5.0
internal class CellularNetworkManager(context: Context) : NetworkManager {

    private val cellularInfo by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private var cellularNetworkCallBack: ConnectivityManager.NetworkCallback? = null

    private var timeoutTask: TimerTask? = null

    private val tracer = TraceCollector.instance

    /**
     * Request the @param url on the mobile device over the mobile data connection, and follow redirects.
     * The redirect may contain URL whose protocol may be HTTP or HTTP.
     * @param url to be requested
     * @return A true if the request was successfully made on a cellular network, otherwise false
     */
    override fun check(url: URL): Boolean {
        var calledOnCellularNetwork = false
        tracer.addDebug(Log.DEBUG, TAG, "Triggering open check url")

        checkNetworks()
        execute {
            calledOnCellularNetwork = it
            if (it) {
                tracer.addDebug(Log.DEBUG, TAG, "-> After forcing isAvailable? ${isCellularAvailable()}")
                tracer.addDebug(Log.DEBUG, TAG, "-> After forcing isBound? ${isCellularBoundToProcess()}")
                // We have Mobile Data registered and bound for use
                // However, user may still have no data plan!
                // Phone Check needs to be done on a socket for 2 reasons:
                // - Redirects may be HTTP rather than HTTPs, as OkHTTP will raise Exception for clearText
                // - We are not interested in the full response body, headers etc.
                val cs = ClientSocket()
                cs.check(url)
            } else {
                tracer.addDebug(Log.DEBUG, TAG, "We do not have a path")
            }
            checkNetworks()
        }

        return calledOnCellularNetwork
    }

    override fun checkWithTrace(url: URL): TraceInfo {
        tracer.startTrace()
        val isConnectedOnCellular = check(url)
        tracer.isTraceCollectedOnCellularNetwork = isConnectedOnCellular
        val traceInfo = tracer.getTrace()
        tracer.stopTrace()
        return traceInfo
    }

    override fun getJSON(url: URL): JSONObject? {
        var calledOnCellularNetwork = false
        tracer.addDebug(Log.DEBUG, TAG, "Triggering get url")
        var json: JSONObject? = null

        checkNetworks()
        execute {
            calledOnCellularNetwork = it
            if (it) {
                tracer.addDebug(Log.DEBUG, TAG, "-> After forcing isAvailable? ${isCellularAvailable()}")
                tracer.addDebug(Log.DEBUG, TAG, "-> After forcing isBound? ${isCellularBoundToProcess()}")
                // We have Mobile Data registered and bound for use
                // However, user may still have no data plan!
                // Phone Check needs to be done on a socket for 2 reasons:
                // - Redirects may be HTTP rather than HTTPs, as OkHTTP will raise Exception for clearText
                // - We are not interested in the full response body, headers etc.
                val cs = ClientSocket()
                json = cs.getJSON(url)
            } else {
                tracer.addDebug(Log.DEBUG, TAG, "We do not have a path")
            }
            checkNetworks()
        }
        return json
    }

    private fun execute(onCompletion: (isSuccess: Boolean) -> Unit) {
        val lock = ReentrantLock()
        val condition = lock.newCondition()

        val capabilities = intArrayOf(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val transportTypes = intArrayOf(NetworkCapabilities.TRANSPORT_CELLULAR)

        forceCellular(capabilities, transportTypes) { isOnCellular ->
            lock.withLock {
                // We have Mobile Data registered and bound for use
                // However, user may still have no data plan!
                onCompletion(isOnCellular)
                condition.signal()
            }
        }

        lock.withLock {
            condition.await()
        }
    }

    /**
     * Configures a network with the capabilities and transport types, registers it and when it is
     * available calls onCompletion lambda with a value of true.
     * 5/20 secs timeout applies if the network never registers, then the lambda is called with
     * a value of false.
     *
     * Requests cellular network. Even though the device may have mobile data, and enabled,
     * some Android devices may not show it on the available networks list. They tend to set WiFi
     * as the default and active network. This method requests the mobile data network to be
     * available, and when available it bind the network to the process to be used later.
     *
     * Whether Cellular Network is Active Network OR (Available and Bound to the process)
     * isCellularActiveNetwork() || (isCellularAvailable() && isCellularBoundToProcess())
     * OR NOT
     * We are requesting cellular data network. If it it no disabled by the user or by network,
     * it should be available. A further optimisation can be done perhaps, with helper check methods.
     *
     */
    @Synchronized
    private fun forceCellular(
        capabilities: IntArray,
        transportTypes: IntArray,
        onCompletion: (isSuccess: Boolean) -> Unit
    ) {

        tracer.addDebug(Log.DEBUG, TAG, "------ Forcing Cellular ------")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!cellularInfo.isDataEnabled) {
                Log.d(TAG, "Mobile Data is NOT enabled, we can not force cellular!")
                Thread {
                    tracer.addDebug(
                        Log.DEBUG,
                        TAG,
                        "Calling completion -- Is Main thread? ${isMainThread()}"
                    )
                    onCompletion(false)
                }.start()
                return
            } else {
                tracer.addDebug(Log.DEBUG, TAG, "-> Mobile Data is Enabled!")
            }
        }

        if (cellularNetworkCallBack == null) {
            cellularNetworkCallBack = object : ConnectivityManager.NetworkCallback() {

                override fun onAvailable(network: Network) {
                    tracer.addDebug(Log.DEBUG, TAG, "Cellular OnAvailable:")
                    networkInfo(network)
                    try {
                        // Binds the current process to network.  All Sockets created in the future
                        // (and not explicitly bound via a bound SocketFactory from {@link Network#getSocketFactory() Network.getSocketFactory()})
                        // will be bound to network.
                        tracer.addDebug(Log.DEBUG, TAG, "  Binding to process:")
                        bind(network)
                        // OR you can bind the socket to the Network
                        // network.bindSocket()
                        tracer.addDebug(Log.DEBUG, TAG, "  Binding finished. Is Main thread? ${isMainThread()}")
                        cancelTimeout()
                        onCompletion(true) // Network request needs to be done in this lambda
                    } catch (e: IllegalStateException) {
                        tracer.addDebug(Log.ERROR, TAG, "ConnectivityManager.NetworkCallback.onAvailable: $e")
                        cancelTimeout()
                        onCompletion(false)
                    } finally {
                        // Release the request when done.
                        unregisterCellularNetworkListener()
                        bind(null)
                    }
                }

                override fun onLost(network: Network) {
                    tracer.addDebug(Log.DEBUG, TAG, "Cellular OnLost:")
                    networkInfo(network)
                    super.onLost(network)
                }

                override fun onUnavailable() {
                    tracer.addDebug(Log.DEBUG, TAG, "Cellular onUnavailable")
                    // When this method gets called due to timeout, the callback will automatically be unregistered
                    // So no need to call unregisterCellularNetworkListener()
                    // But we should null it
                    cellularNetworkCallBack = null
                    onCompletion(false)
                    super.onUnavailable()
                }
            }

            tracer.addDebug(Log.DEBUG, TAG, "Creating a network builder on Main thread? ${isMainThread()}")

            val request = NetworkRequest.Builder()
            // Just in case as per Documentation
            request.removeTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            request.removeTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH)

            for (capability in capabilities) {
                request.addCapability(capability)
            }
            for (transportType in transportTypes) {
                request.addTransportType(transportType)
            }

            tracer.addDebug(Log.DEBUG, TAG, "Cellular requested")

            requestNetwork(request.build(), onCompletion)

            tracer.addDebug(Log.DEBUG, TAG, "Forcing Cellular - Requesting to registered...")
        } else {
            // Perhaps there is already one registered, and in progress or waiting to be timed out
            tracer.addDebug(Log.DEBUG, TAG, "There is already a Listener registered.")
        }
    }

    private fun switchToAvailableNetwork() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            for (network in connectivityManager.allNetworks) {
                if (isWIFI(network)) {
                    bind(network)
                    break
                }
            }
        } else {
            bind(connectivityManager.activeNetwork)
        }
    }

    private fun bind(network: Network?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            ConnectivityManager.setProcessDefaultNetwork(network)
        } else {
            connectivityManager.bindProcessToNetwork(network) // API Level 23, 6.0 Marsh
        }
    }

    private fun requestNetwork(request: NetworkRequest, onCompletion: (isSuccess: Boolean) -> Unit) {
        // The network request will live, until unregisterNetworkCallback is called or app exit.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { // API Level 26
            timeoutTask = Timer("Setting Up", true).schedule(TIME_OUT) {
                tracer.addDebug(Log.DEBUG, TAG, "Timeout...")
                Thread { onCompletion(false) }.start()
            }
            connectivityManager.requestNetwork(
                request,
                cellularNetworkCallBack as ConnectivityManager.NetworkCallback
            )
        } else {
            connectivityManager.requestNetwork(
                request,
                cellularNetworkCallBack as ConnectivityManager.NetworkCallback,
                TIME_OUT.toInt()
            )
        }
    }

    private fun cancelTimeout() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            tracer.addDebug(Log.DEBUG, TAG, "Cancelling timeout")
            timeoutTask?.cancel()
        }
    }

    private fun unregisterCellularNetworkListener() {
        Log.d(TAG, "UnregisteringCellularNetworkListener")
        cellularNetworkCallBack?.let {
            tracer.addDebug(Log.DEBUG, TAG, "CallBack available, unregistering it.")
            connectivityManager.unregisterNetworkCallback(cellularNetworkCallBack as ConnectivityManager.NetworkCallback)
            cellularNetworkCallBack = null
        }
    }

    private fun isMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }

    private fun checkNetworks() {
        tracer.addDebug(Log.DEBUG, TAG, "----- Check ------")
        tracer.addDebug(Log.DEBUG, TAG, "Is Default Network Active? " + connectivityManager.isDefaultNetworkActive.toString())
        boundNetwork()
        activeNetworkInfo()
        availableNetworks()
    }

    private fun isCellular(network: Network): Boolean {
        val caps = connectivityManager.getNetworkCapabilities(network)
        caps?.let {
            if (it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            ) {
                return true
            }
        }
        return false
    }

    private fun isWIFI(network: Network): Boolean {
        val caps = connectivityManager.getNetworkCapabilities(network)
        caps?.let {
            if (it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            ) {
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

    private fun isCellularBoundToProcess(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // API 23
            connectivityManager.boundNetworkForProcess?.let {
                return isCellular(it)
            }
        }
        return false
    }

    companion object {
        private const val TAG = "CellularNetworkManager"
        private const val TIME_OUT: Long = 5000
        private const val HEADER_USER_AGENT = "User-Agent"
    }

    private fun boundNetwork() { // API 23
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tracer.addDebug(Log.DEBUG, TAG, "----- Bound network ----")
            connectivityManager.boundNetworkForProcess?.let { networkInfo(it) }
        }
    }

    private fun activeNetworkInfo() { // API 23
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tracer.addDebug(Log.DEBUG, TAG, "---- Active network ----")
            connectivityManager.activeNetwork?.let { networkInfo(it) }
        }
    }

    private fun isCellularActiveNetwork(): Boolean { // API 23
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connectivityManager.activeNetwork?.let {
                return isCellular(it)
            }
        }
        return false
    }

    @RequiresApi(api = Build.VERSION_CODES.R) // 30
    private fun networkType(capability: NetworkCapabilities) {
        when (capability.networkSpecifier) {
            is TelephonyNetworkSpecifier -> Log.d(TAG, "Cellular network")
            is WifiNetworkSpecifier -> Log.d(TAG, "Wifi network")
            is WifiAwareNetworkSpecifier -> Log.d(TAG, "Wifi Aware network")
        }
    }

    private fun availableNetworks() {
        tracer.addDebug(Log.DEBUG, TAG, "----------Available Networks----------")

        val networks = connectivityManager.allNetworks
        for (network in networks) {

            networkInfo(network)

            val caps = connectivityManager.getNetworkCapabilities(network)
            caps?.let {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // IF >= API Level 30
                    networkType(it)
                    tracer.addDebug(Log.DEBUG, TAG, "Signal Strength : " + it.signalStrength)

                    val transportInfo = it.transportInfo // API Level 29, WifiAwareNetworkInfo, WifiInfo
                    if (it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        tracer.addDebug(Log.DEBUG, TAG, "Cap: Internet Capability")
                    }
                    if (it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        tracer.addDebug(Log.DEBUG, TAG, "Cap: Cellular")
                    }
                    if (it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)) {
                        tracer.addDebug(Log.DEBUG, TAG, "Cap: Wifi Aware")
                    }
                    if (it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        tracer.addDebug(Log.DEBUG, TAG, "Cap: Wifi")
                    }
                    if (it.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                        tracer.addDebug(Log.DEBUG, TAG, "Cap: Bluetooth")
                    }
                }
            }
        }
    }

    private fun networkInfo(network: Network) {
        tracer.addDebug(Log.DEBUG, TAG, "Name:" + linkName(network))
        linkAddresses(network)
    }

    private fun linkName(network: Network): String {
        val networkLinkProperties = connectivityManager.getLinkProperties(network)
        return networkLinkProperties?.interfaceName ?: "None"
    }

    private fun linkAddresses(network: Network) {
        val activeNetworkLinkProperties = connectivityManager.getLinkProperties(network)

        activeNetworkLinkProperties?.linkAddresses?.let {
            for (address in it) {
                Log.d(TAG, "Address: $address")
            }
        }
    }
}
