package id.tru.sdk.network

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import id.tru.sdk.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import javax.net.ssl.SSLSocketFactory

@RequiresApi(Build.VERSION_CODES.LOLLIPOP) //API Level 21
internal class ClientSocket {
    private lateinit var socket: Socket
    private lateinit var output: OutputStream
    private lateinit var input: BufferedReader

    private val tracer = TraceCollector()

    /**
     * To be used for phoneCheck.
     * Sends an HTTP/HTTPS request over a Socket, and follows redirects up to MAX_REDIRECT_COUNT.
     */
    fun check(url: URL) {
        var redirectURL: URL? = null
        var redirectCount = 0
        do {
            redirectCount += 1
            val nurl = redirectURL ?: url
            tracer.addDebug(Log.DEBUG, TAG, "Requesting: $nurl")
            startConnection(nurl)
            redirectURL = sendCommand(nurl)
            stopConnection()
        } while (redirectURL != null && redirectCount <= MAX_REDIRECT_COUNT)
        tracer.addDebug(Log.DEBUG, TAG, "Check complete")
    }

    fun checkWithTrace(url: URL): TraceInfo {
        tracer.startTrace()
        check(url = url)
        val traceInfo = tracer.getTrace()
        tracer.stopTrace()
        return traceInfo
    }

    private fun makeHTTPCommand(url: URL): String {
        val CRLF = "\r\n"
        val cmd = StringBuffer()
        cmd.append("GET " + url.path)
        if (url.query != null) {
            cmd.append("?" + url.query)
        }
        cmd.append(" HTTP/1.1$CRLF")
        cmd.append("Host: " + url.host + CRLF)
        val userAgent = userAgent()
        cmd.append("$HEADER_USER_AGENT: $userAgent$CRLF")
        cmd.append("Accept: */*$CRLF")
        cmd.append("Connection: close$CRLF$CRLF")
        return cmd.toString()
    }

    private fun sendCommand(url: URL): URL? {
        val command = makeHTTPCommand(url)
        return sendRequest(command)
    }

    private fun startConnection(url: URL) {
        tracer.addDebug(Log.DEBUG, TAG, "start : ${url.host} ${url.protocol}")
        tracer.addTrace("\nStart connection ${url.host} ${url.protocol} ${DateUtils.now()}\n")
        socket = if (url.protocol == "https") {
            SSLSocketFactory.getDefault().createSocket(url.host, 443)
        } else {
            Socket(url.host, 80)
        }

        output = socket.getOutputStream()
        input = BufferedReader(InputStreamReader(socket.inputStream))

        tracer.addDebug(Log.DEBUG, TAG, "Client connected : ${socket.inetAddress.hostAddress} ${socket.port}")
        tracer.addTrace("Connected ${DateUtils.now()}\n")
    }

    private fun sendRequest(message: String): URL? {
        tracer.addDebug(Log.DEBUG, TAG, "Client sending \n$message\n")
        tracer.addTrace(message)
        val bytesOfRequest: ByteArray =
            message.toByteArray(Charset.forName(StandardCharsets.UTF_8.name()))
        output.write(bytesOfRequest)
        output.flush()

        tracer.addDebug(Log.DEBUG, TAG, "Response " + "\n")
        tracer.addTrace("Response - ${DateUtils.now()} \n")

        while (socket.isConnected) {
            var line = input.readLine();
            if (line != null) {
                tracer.addDebug(Log.DEBUG, TAG, line)
                tracer.addTrace(line)
                if (line.startsWith("HTTP/")) {
                    val parts = line.split(" ")
                    if (parts.isNotEmpty() && parts.size >= 2) {
                        val status = Integer.valueOf(parts[1])
                        tracer.addDebug(Log.DEBUG, TAG, "status: ${status}\n")
                        if (status < 300 || status > 310) {
                            tracer.addDebug(Log.DEBUG, TAG, "Status - $status")
                            tracer.addTrace("Status - $status ${DateUtils.now()}\n")
                            break
                        }
                    }
                } else if (line.contains("ocation:")) {
                    var parts = line.split(" ")
                    if (parts.isNotEmpty() && parts.size == 2) {
                        var redirect = parts[1]
                        tracer.addDebug(Log.DEBUG, TAG, "Found redirect")
                        tracer.addTrace("Found redirect - ${DateUtils.now()} \n")
                        return URL(redirect);
                    }
                }
            } else {
                tracer.addDebug(Log.ERROR, TAG, "Error reading the response.")
                break
            }
        }
        return null
    }

    private fun stopConnection() {
        tracer.addDebug(Log.DEBUG, TAG, "closed the connection ${socket.inetAddress.hostAddress}")
        try {
            input.close()
            output.close()
            socket.close()
        } catch (e: Throwable) {
            tracer.addDebug(Log.ERROR, TAG, "Exception received whilst closing the socket ${e.localizedMessage}")
        }
    }

    companion object {
        private const val TAG = "CellularClient"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val MAX_REDIRECT_COUNT = 10
    }
}