package id.tru.sdk.network

import android.os.Build
import android.util.Log
import id.tru.sdk.BuildConfig


class TraceCollector {
    private val debugInfo = DebugInfo()
    private val trace = StringBuilder()
    private var isTraceEnabled = false
    var isTraceCollectedOnCellularNetwork = false

    @Synchronized
    fun startTrace() {
        debugInfo.clear()
        trace.clear()
        isTraceEnabled = true
    }

    @Synchronized
    fun stopTrace() {
        isTraceEnabled = false
        trace.clear()
        debugInfo.clear()
    }

    fun getTrace(): TraceInfo {
        return TraceInfo(trace.toString(), debugInfo)
    }

    @Synchronized
    fun addTrace(log: String) {
        if (isTraceEnabled) trace.append(log + "\n")
    }

    @Synchronized
    fun addDebug(priority: Int, tag: String, log: String) {
        if (isTraceEnabled) debugInfo.addLog(priority, tag, log)
    }

    fun getDebugInfo(): DebugInfo {
        return debugInfo
    }

}

data class TraceInfo(val trace: String, val debugInfo: DebugInfo)

class DebugInfo {
    private val bufferMap by lazy {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val version = Build.VERSION.SDK_INT
        val versionRelease = Build.VERSION.RELEASE
        mutableMapOf<String, String>( dateUtils.now() to "Debug Trace starting on: $manufacturer, $model, $version, $versionRelease",
            dateUtils.now() to "User-Agent: ${userAgent()}")
    }

    private val dateUtils: DateUtils by lazy {
        DateUtils()
    }

    @Synchronized
    fun clear() {
        bufferMap.clear()
    }

    @Synchronized
    fun addLog(priority: Int, tag: String, msg: String) {
        bufferMap[dateUtils.now()] = "$tag - $msg"

        when(priority) {
            2 -> Log.v(tag, msg)//VERBOSE
            3 -> Log.d(tag, msg)//DEBUG
            4 -> Log.i(tag, msg)//INFO
            5 -> Log.w(tag, msg)//WARN
            6 -> Log.e(tag, msg)//ERROR
            else -> { //Fall back to //DEBUG
                Log.d(tag, msg)
            }
        }
    }

    override fun toString(): String {
        var buffer = StringBuffer()
        val sorted = bufferMap.toSortedMap()
        val skeys = sorted.keys
        for (key in skeys) {
            buffer.append("$key: ${sorted[key]}\n")
        }
        return buffer.toString()
    }
}

fun userAgent(): String {
    return "tru-sdk-android" + "/" + BuildConfig.VERSION_NAME + " " + "Android" + "/" + Build.VERSION.RELEASE
}

