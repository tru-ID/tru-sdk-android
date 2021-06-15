package id.tru.sdk.network

class TraceCollector {
    private var trace = StringBuilder()
    private var isTraceEnabled = false

    fun startTrace() {
        trace.clear()
        isTraceEnabled = true
    }

    fun stopTrace() {
        isTraceEnabled = false
        trace.clear()
    }

    fun getTrace(): String {
        return trace.toString()
    }

    fun addTrace(log: String) {
        if (isTraceEnabled) trace.append(log + "\n")
    }

}