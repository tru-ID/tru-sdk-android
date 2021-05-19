package id.tru.sdk.network

import android.os.Build
import androidx.annotation.RequiresApi
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLSocketFactory
import android.util.Log

@RequiresApi(Build.VERSION_CODES.LOLLIPOP) //API Level 21
class ClientSocket {
    private lateinit var socket: Socket
    private lateinit var output: OutputStream
    private lateinit var input: BufferedReader

    /**
     * Sends an HTTP(S) request over a Socket, and follows redirects up to MAX_REDIRECT_COUNT.
     */
    fun check(url: URL) {
        var redirectURL: URL? = null
        var redirectCount = 0
        do {
            redirectCount += 1
            startConnection(redirectURL ?: url)
            redirectURL = sendCommand(url)
            stopConnection()
        } while (redirectURL != null && redirectCount <= MAX_REDIRECT_COUNT)
    }

    private fun makeHTTPCommand(url: URL): String {
        val CRLF = "\r\n"
        val cmd = StringBuffer()
        cmd.append("GET "+url.path)
        if (url.query!=null) {
            cmd.append("?"+url.query)
        }
        cmd.append(" HTTP/1.1$CRLF")
        cmd.append("Host: "+ url.host+CRLF)
        cmd.append("User-Agent: tru-sdk-android/wip$CRLF")
        cmd.append("Accept: */*$CRLF")
        cmd.append("Connection: close$CRLF$CRLF")
        return cmd.toString()
    }

    private fun sendCommand(url: URL): URL? {
        val command = makeHTTPCommand(url)
        return sendRequest(command)
    }

    private fun startConnection(url: URL) {
        Log.d(TAG, "start : ${url.host} ${url.protocol}")

        socket = if (url.protocol == "https") {
            SSLSocketFactory.getDefault().createSocket(url.host, 443)
        } else {
            Socket(url.host, 80)
        }

        output = socket.getOutputStream()
        input = BufferedReader(InputStreamReader(socket.inputStream))

        Log.d(TAG, "Client connected : ${socket.inetAddress.hostAddress} ${socket.port}")
    }

    private fun sendRequest(message: String): URL? {
        Log.d(TAG, "Client sending \n$message\n")

        val bytesOfRequest: ByteArray = message.toByteArray(Charset.forName(StandardCharsets.UTF_8.name()))
        output.write(bytesOfRequest)
        output.flush()

        Log.d(TAG, "Response " +"\n")

        while (socket.isConnected) {
            var line = input.readLine();
            if (line != null) {
                Log.d(TAG, line)
                if (line.startsWith("HTTP/")) {
                    val parts = line.split(" ")
                    if (!parts.isEmpty() && parts.size >= 2) {
                        val status = Integer.valueOf(parts[1])
                        Log.d(TAG, "status: ${status}\n")
                        /*if (status == 200) {
                            continue
                        } else*/ if ( status <300 || status > 310) {
                            Log.d(TAG, "Status - ${status}")
                            break
                        }
                    }
                } else if (line.contains("ocation:")) {
                    var parts = line.split(" ")
                    if (parts.isNotEmpty() && parts.size==2) {
                        var redirect = parts[1]
                        Log.d(TAG, "Found redirect")
                        return URL(redirect);
                    }
                }
            } else {
                Log.e(TAG, "Error reading the response.")
                break
            }
        }
        return null
    }

    private fun stopConnection() {
        input.close()
        output.close()
        socket.close()
        Log.d(TAG, "${socket.inetAddress.hostAddress} closed the connection")
    }

    companion object {
        private const val TAG = "CellularClient"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val SDK_USER_AGENT = "tru-sdk-android"
        private const val MAX_REDIRECT_COUNT = 10
    }
}