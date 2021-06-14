package id.tru.sdk.network

class TraceCollector {
    private var trace = StringBuilder()
    private var traceOn = false

    fun startTrace() {
        trace.clear()
        traceOn = true
    }

    fun stopTrace() {
        traceOn = false
        trace.clear()
    }

    fun getTrace(): String {
        return trace.toString()
    }

    fun addTrace(log: String) {
        if (traceOn) trace.append(log + "\n")
    }

}