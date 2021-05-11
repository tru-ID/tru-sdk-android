package id.tru.sdk.network

import android.net.Network
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import java.net.URL
import java.nio.charset.Charset
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLSocketFactory

@RequiresApi(Build.VERSION_CODES.O)
class ClientSocket {
    lateinit var client: Socket
    lateinit var output: OutputStream
    lateinit var input: BufferedReader
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

    fun check(url: URL) {
        startConnection(url)
        var r = sendCommand(url)
        stopConnection()
        if (r!=null)
            check(URL(r))
    }

    fun sendCommand(url: URL): String? {
        var CRLF = "\r\n"
        var cmd = StringBuffer()
        cmd.append("GET "+url.path)
        if (url.query!=null) {
            cmd.append("?"+url.query)
        }
        cmd.append(" HTTP/1.1$CRLF")
        cmd.append("Host: "+ url.host+CRLF)
        cmd.append("User-Agent: tru-sdk-android/wip$CRLF")
        cmd.append("Accept: */*$CRLF")
        cmd.append("Connection: close$CRLF$CRLF")
        return sendMessage(cmd.toString())
    }

    fun startConnection(url: URL) {
        var port = 80
        println("start : ${url.host} ${url.protocol}")
        addTrace("\nStart connection ${url.host} ${url.protocol} ${DateTimeFormatter.ISO_INSTANT.format(Instant.now()).toString()}\n")
        if (url.protocol == "https") {
            port = 443;
            client = SSLSocketFactory.getDefault().createSocket(url.host, port)
        } else
            client = Socket(url.host, port)
        output = client.getOutputStream()
        input = BufferedReader(InputStreamReader(client.inputStream))
        println("Client connected : ${client.inetAddress.hostAddress} ${client.port} ${DateTimeFormatter.ISO_INSTANT.format(Instant.now()).toString()}")
        addTrace("Connected ${DateTimeFormatter.ISO_INSTANT.format(Instant.now()).toString()}\n")

    }

    fun sendMessage(message: String): String? {
        println("Client sending \n$message\n")
        addTrace(message)
        val bytesOfRequest: ByteArray = message.toByteArray(Charset.forName("UTF-8)"))
        output.write(bytesOfRequest)
        println("Response\n")
        addTrace("Response - " + DateTimeFormatter.ISO_INSTANT.format(Instant.now()).toString() +"\n")
        while (true) {
            var str = input.readLine();
            if (str !=null) {
                println(str)
                addTrace(str)
                if (str.startsWith("HTTP/")) {
                    var parts = str.split(" ")
                    if (!parts.isEmpty() && parts.size>=2) {
                        var status = Integer.valueOf(parts[1])
                        println("status: ${status}\n")
                        /*if (status == 200) {
                            continue
                        } else*/ if ( status <300 || status > 310) {
                            addTrace("Status - ${status} ${DateTimeFormatter.ISO_INSTANT.format(Instant.now()).toString()}\n")
                            break
                        }
                    }
                } else if (str.contains("ocation:")) {
                    var parts = str.split(" ")
                    if (!parts.isEmpty() && parts.size==2) {
                        var redirect = parts[1]
                        println("redirect: ${redirect}\n")
                        addTrace("Found redirect - " + DateTimeFormatter.ISO_INSTANT.format(Instant.now()).toString() +"\n")
                        return redirect;
                    }
                }
            } else {
                print("fuck off "+DateTimeFormatter.ISO_INSTANT.format(Instant.now()).toString())
                break
            }
        }
        return null
    }

    fun stopConnection() {
        client.close()
        input.close()
        output.close()
        println("${client.inetAddress.hostAddress} closed the connection")
    }

}