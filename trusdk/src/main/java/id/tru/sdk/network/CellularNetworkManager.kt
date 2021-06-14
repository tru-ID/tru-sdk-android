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
import id.tru.sdk.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.IOException
import org.json.JSONException
import org.json.JSONObject
import java.net.URL
import java.security.KeyStore
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.schedule
import kotlin.concurrent.withLock


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

    /**
     * Request the @param url on the mobile device over the mobile data connection, and follow redirects.
     * The redirect may contain URL whose protocol may be HTTP or HTTP.
     * @param url to be requested
     * @return A true if the request was successfully made on a cellular network, otherwise false
     */
    override fun check(url: URL): Boolean {
        var calledOnCellularNetwork = false
        Log.d(TAG, "Triggering open check url")

        checkNetworks()
        execute {
            calledOnCellularNetwork = it
            if (it) {
                Log.d(TAG, "-> After forcing isAvailable? ${isCellularAvailable()}")
                Log.d(TAG, "-> After forcing isBound? ${isCellularBoundToProcess()}")
                // We have Mobile Data registered and bound for use
                // However, user may still have no data plan!
                // Phone Check needs to be done on a socket for 2 reasons:
                // - Redirects may be HTTP rather than HTTPs, as OkHTTP will raise Exception for clearText
                // - We are not interested in the full response body, headers etc.
                var cs = ClientSocket()
                cs.check(url)
            } else {
                Log.d(TAG, "We do not have a path")
            }
            checkNetworks()
        }
        return calledOnCellularNetwork
    }

    override fun checkWithTrace(url: URL): Pair<Boolean, String> {
        var calledOnCellularNetwork = false
        var trace = ""
        Log.d(TAG, "Triggering open check url")

        checkNetworks()
        execute {
            calledOnCellularNetwork = it
            if (it) {
                Log.d(TAG, "-> After forcing isAvailable? ${isCellularAvailable()}")
                Log.d(TAG, "-> After forcing isBound? ${isCellularBoundToProcess()}")
                // We have Mobile Data registered and bound for use
                // However, user may still have no data plan!
                // Phone Check needs to be done on a socket for 2 reasons:
                // - Redirects may be HTTP rather than HTTPs, as OkHTTP will raise Exception for clearText
                // - We are not interested in the full response body, headers etc.
                var cs = ClientSocket()
                trace = cs.checkWithTrace(url)
            } else {
                Log.d(TAG, "We do not have a path")
            }
            checkNetworks()
        }
        return Pair(calledOnCellularNetwork, trace)
    }

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
    override fun requestSync(
        url: URL,
        method: String,
        body: RequestBody?): JSONObject? {

        var res: JSONObject? = null
        try {
            val responseBody = requestSync(url, method, null, null)
            responseBody?.let { res = JSONObject(it) }
        } catch (e: JSONException) { }

        return res
    }

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
    override fun requestSync(
        url: URL,
        method: String,
        headerName: String?,
        headerValue: String?,
        body: RequestBody?): String? {

        var responseBody: String? = null
        checkNetworks()
        execute { isOnCellular ->
            Log.d(TAG, "-> After forcing isAvailable? ${isCellularAvailable()}")
            Log.d(TAG, "-> After forcing isBound? ${isCellularBoundToProcess()}")

            if (isOnCellular) {
                /*
                * We know that Network is bound to the process and any sockets created
                * after this point will use that Network.
                * OkHTTP should be using this network and the SocketFactory. If we can prove otherwise,
                * it is possible to set the SocketFactory on OkHttpClient, if you can push the available Network's socketFactory.
                *
                * .socketFactory(it) // HTTP
                * .sslSocketFactory(socketFactory as SSLSocketFactory, trust()) //HTTPS
                *
                * Setting SSLSocketFactory is a bit unconventional though (see trust() method)
                 */
                val client = OkHttpClient()

                val requestPrototype = Request.Builder()
                requestPrototype.method(method, body)
                requestPrototype.url(url)
                requestPrototype.addHeader(
                    HEADER_USER_AGENT,
                    SDK_USER_AGENT + "/" + BuildConfig.VERSION_NAME + " " +
                            "Android" + "/" + Build.VERSION.RELEASE
                )

                if (headerName != null && headerValue != null) {
                    requestPrototype.addHeader(headerName, headerValue)
                }

                val request = requestPrototype.build()

                responseBody = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")
                    response.body?.string()
                }
            } else {
                Log.d(TAG, "We do not have a path")
            }
        }
        return responseBody
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
        onCompletion: (isSuccess: Boolean) -> Unit) {

        Log.d(TAG, "------ Forcing Cellular ------")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!cellularInfo.isDataEnabled) {
                Log.d(TAG, "Mobile Data is NOT enabled, we can not force cellular!")
                Thread(Runnable {
                    Log.d(TAG, "Calling completion -- Is Main thread? ${isMainThread()}")
                    onCompletion(false)
                }).start()
                return
            } else {
                Log.d(TAG, "-> Mobile Data is Enabled!")
            }
        }

        if (cellularNetworkCallBack == null) {
            cellularNetworkCallBack = object : ConnectivityManager.NetworkCallback() {

                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Cellular OnAvailable:")
                    networkInfo(network)
                    try {
                        //Binds the current process to network.  All Sockets created in the future
                        // (and not explicitly bound via a bound SocketFactory from {@link Network#getSocketFactory() Network.getSocketFactory()})
                        // will be bound to network.
                        Log.d(TAG, "  Binding to process:")
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                            ConnectivityManager.setProcessDefaultNetwork(network)
                        } else {
                            connectivityManager.bindProcessToNetwork(network) //API Level 23, 6.0 Marsh
                        }
                        //OR you can bind the socket to the Network
                        //network.bindSocket()
                        Log.d(TAG, "  Binding finished. Is Main thread? ${isMainThread()}")
                        cancelTimeout()
                        onCompletion(true) //Network request needs to be done in this lambda
                    } catch (e: IllegalStateException) {
                        Log.e(TAG, "ConnectivityManager.NetworkCallback.onAvailable: ", e)
                        cancelTimeout()
                        onCompletion(false)
                    } finally {
                        // Release the request when done.
                        unregisterCellularNetworkListener()
                    }
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "Cellular OnLost:")
                    networkInfo(network)
                    super.onLost(network)
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

            Log.d(TAG, "Creating a network builder on Main thread? ${isMainThread()}")

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

            Log.d(TAG, "Cellular requested")

            requestNetwork(request.build(), onCompletion)

            Log.d(TAG, "Forcing Cellular - Requesting to registered...")
        } else {
            //Perhaps there is already one registered, and in progress or waiting to be timed out
            Log.d(TAG, "There is already a Listener registered.")
        }

    }

    private fun requestNetwork(request: NetworkRequest, onCompletion: (isSucess: Boolean) -> Unit) {
        //The network request will live, until unregisterNetworkCallback is called or app exit.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {//API Level 26
            timeoutTask = Timer("Setting Up", true).schedule(TIME_OUT) {
                Log.d(TAG, "Timeout...")
                Thread(Runnable { onCompletion(false) }).start()
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
            Log.d(TAG, "Cancelling timeout")
            timeoutTask?.let { it.cancel() }
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

    private fun checkNetworks() {
        Log.d(TAG, "----- Check ------")
        Log.d(
            TAG,
            "Is Default Network Active? " + connectivityManager.isDefaultNetworkActive.toString()
        )
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { //API 23
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
        private const val SDK_USER_AGENT = "tru-sdk-android"
    }

    private fun boundNetwork() { //API 23
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.d(TAG, "----- Bound network ----")
            connectivityManager.boundNetworkForProcess?.let { networkInfo(it) }
        }
    }

    private fun activeNetworkInfo() { //API 23
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.d(TAG, "---- Active network ----")
            connectivityManager.activeNetwork?.let { networkInfo(it) }
        }
    }

    private fun isCellularActiveNetwork(): Boolean { //API 23
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connectivityManager.activeNetwork?.let {
                return isCellular(it)
            }
        }
        return false
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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // IF >= API Level 30
                    networkType(it)
                    Log.d(TAG, "Signal Strength : " + it.signalStrength)

                    val transportInfo =
                        it.transportInfo // API Level 29, WifiAwareNetworkInfo, WifiInfo
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
            for (address in it) {
                Log.d(TAG, "Address: ${address.toString()}")
            }
        }
    }

    private fun trust(): X509TrustManager {
        val trustManagerFactory: TrustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        )
        trustManagerFactory.init(null as KeyStore?)
        val trustManagers: Array<TrustManager> = trustManagerFactory.trustManagers
        check(!(trustManagers.size != 1 || trustManagers[0] !is X509TrustManager)) {
            ("Unexpected default trust managers:" + Arrays.toString(trustManagers))
        }
        return trustManagers[0] as X509TrustManager
//        val sslContext: SSLContext = SSLContext.getInstance("TLS")
//        sslContext.init(null, arrayOf<TrustManager>(trustManager), null)
//        val sslSocketFactory: SSLSocketFactory = sslContext.getSocketFactory()
    }

}