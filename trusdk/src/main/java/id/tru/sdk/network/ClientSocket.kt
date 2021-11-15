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

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLSocketFactory
import org.apache.commons.io.IOUtils
import org.json.JSONException
import org.json.JSONObject

@RequiresApi(Build.VERSION_CODES.LOLLIPOP) // API Level 21
internal class ClientSocket constructor(var tracer: TraceCollector = TraceCollector.instance) {
    private lateinit var socket: Socket
    private lateinit var output: OutputStream
    private lateinit var input: BufferedReader

    /**
     * To be used for phoneCheck.
     * Sends an HTTP/HTTPS request over a Socket, and follows redirects up to MAX_REDIRECT_COUNT.
     */
    fun check(url: URL) {
        var redirectURL: URL? = null
        var redirectCount = 0
        var result: ResultHandler?
        do {
            redirectCount += 1
            val nurl = redirectURL ?: url
            tracer.addDebug(Log.DEBUG, TAG, "Requesting: $nurl")
            startConnection(nurl)
            result = sendCommand(nurl)
            if (result != null && result.getRedirect() != null)
                redirectURL = result.getRedirect()
            else
                redirectURL = null
            stopConnection()
        } while (redirectURL != null && redirectCount <= MAX_REDIRECT_COUNT)
        if (result?.getBody() != null) {
            startConnection(url)
            sendPatchCommand(url, result.getBody())
            stopConnection()
        }
        tracer.addDebug(Log.DEBUG, TAG, "Check complete")
    }

    fun checkWithTrace(url: URL) {
        check(url = url)
    }

    fun getJSON(url: URL): JSONObject? {
        var result: ResultHandler?
        tracer.addDebug(Log.DEBUG, TAG, "Requesting: $url")
        startConnection(url)
        result = sendCommand(url)
        stopConnection()
        if (result?.getBody() != null) {
            try {
                return JSONObject(result.getBody())
            } catch (e: JSONException) {
                tracer.addDebug(Log.ERROR, TAG, "JSONException: $e")
            }
        }
        return null
    }

    private fun makeHTTPCommand(url: URL): String {
        val CRLF = "\r\n"
        val cmd = StringBuffer()
        cmd.append("GET " + url.path)
        if (url.query != null) {
            cmd.append("?" + url.query)
        }
        cmd.append(" HTTP/1.1$CRLF")
        cmd.append("Host: " + url.host)
        if (url.protocol == "https" && url.port > 0 && url.port != PORT_443) {
            cmd.append(":" + url.port)
        } else if (url.protocol == "http" && url.port > 0 && url.port != PORT_80) {
            cmd.append(":" + url.port)
        }
        cmd.append(CRLF)
        val userAgent = userAgent()
        cmd.append("$HEADER_USER_AGENT: $userAgent$CRLF")
        cmd.append("Accept: text/html,application/xhtml+xml,application/xml,*/*$CRLF")
        cmd.append("Connection: close$CRLF$CRLF")
        return cmd.toString()
    }

    private fun sendCommand(url: URL): ResultHandler? {
        val command = makeHTTPCommand(url)
        return sendAndReceive(url, command)
    }

    fun sendPatchCommand(url: URL, payload: String?): ResultHandler? {
        val command = makePatchHTTPCommand(url, payload)
        return sendAndReceive(url, command.toString())
    }

    private fun makePatchHTTPCommand(url: URL, payload: String?): String {
        var CRLF = "\r\n"
        var cmd = StringBuffer()
        var body: String = "[{\"op\": \"add\",\"path\":\"/payload\",\"value\": " + payload + "}]"
        cmd.append("PATCH " + url.path)
        if (url.query != null) {
            cmd.append("?" + url.query)
        }
        cmd.append(" HTTP/1.1$CRLF")
        cmd.append("Host: " + url.host)
        if (url.protocol == "https" && url.port > 0 && url.port != PORT_443) {
            cmd.append(":" + url.port)
        } else if (url.protocol == "http" && url.port > 0 && url.port != PORT_80) {
            cmd.append(":" + url.port)
        }
        cmd.append(CRLF)
        val userAgent = userAgent()
        cmd.append("$HEADER_USER_AGENT: $userAgent$CRLF")
        cmd.append("Accept: */*$CRLF")
        cmd.append("Content-Type: application/json-patch+json$CRLF")
        cmd.append("Content-Length: " + body.length + "$CRLF")
        cmd.append("$CRLF" + body)
        return cmd.toString()
    }

    private fun startConnection(url: URL) {
        var port = PORT_80
        if (url.port > 0) port = url.port
        tracer.addDebug(Log.DEBUG, TAG, "start : ${url.host} ${url.port} ${url.protocol}")
        tracer.addTrace("\nStart connection ${url.host} ${url.port} ${url.protocol} ${DateUtils.now()}\n")
        socket = if (url.protocol == "https") {
            port = PORT_443
            if (url.port > 0) port = url.port
            SSLSocketFactory.getDefault().createSocket(url.host, port)
        } else {
            Socket(url.host, port)
        }

        output = socket.getOutputStream()
        input = BufferedReader(InputStreamReader(socket.inputStream))

        tracer.addDebug(Log.DEBUG, TAG, "Client connected : ${socket.inetAddress.hostAddress} ${socket.port}")
        tracer.addTrace("Connected ${DateUtils.now()}\n")
    }

    private fun sendAndReceive(requestURL: URL, message: String): ResultHandler? {
        tracer.addDebug(Log.DEBUG, TAG, "Client sending \n$message\n")
        tracer.addTrace(message)
        val bytesOfRequest: ByteArray =
                message.toByteArray(Charset.forName(StandardCharsets.UTF_8.name()))
        output.write(bytesOfRequest)
        output.flush()

        tracer.addDebug(Log.DEBUG, TAG, "Response " + "\n")
        tracer.addTrace("Response - ${DateUtils.now()} \n")
        var status: Int = 0
        var body: String? = null
        var type: String = ""
        var result: ResultHandler? = null
        var bodyBegin: Boolean = false
        // convert the entire stream in a String
        var response: String? = IOUtils.toString(input)
        response?.let {
            val lines = response.split("\n")
            for (line in lines) {
                tracer.addDebug(Log.DEBUG, TAG, line)
                tracer.addTrace(line)
                if (line.startsWith("HTTP/")) {
                    val parts = line.split(" ")
                    if (parts.isNotEmpty() && parts.size >= 2) {
                        val s = Integer.valueOf(parts[1])
                        tracer.addDebug(Log.DEBUG, TAG, "status: ${s}\n")
                        if (s == 200) {
                            status = s
                            continue
                        } else if (s < 300 || s > 310) {
                            tracer.addDebug(Log.DEBUG, TAG, "Status - $s")
                            tracer.addTrace("Status - $s ${DateUtils.now()}\n")
                            break
                        }
                    }
                } else if (line.contains("Location:") || line.contains("location:")) {
                    result = parseRedirect(requestURL, line.replace("\r", ""))
                } else if (line.contains("Content-Type:")) {
                    var parts = line.split(" ")
                    if (!parts.isEmpty() && parts.size == 2) {
                        type = parts[1].replace("\r", "")
                    }
                } else if (status == 200 && ("application/json".equals(type) || "application/hal+json".equals(type)) && line.equals("\r")) {
                    bodyBegin = true
                } else if (bodyBegin) {
                    body = if (body != null) body + line.replace("\r", "") else line.replace("\r", "")
                    tracer.addDebug(Log.DEBUG, TAG, "Adding to body - $body\n")
                }
            }
            tracer.addDebug(Log.DEBUG, TAG, "Status - $status\nBody - $body\n")
            tracer.addTrace("Status - $status ${DateUtils.now()}\nBody - $body\n")
            if (status == 200 && body != "") {
                result = ResultHandler(null, parseBodyIntoJSONString(body))
            }
        }
        return result
    }

    fun parseBodyIntoJSONString(body: String?): String? {
        if (body != null) {
            val start = body.indexOf("{")
            var end = body.lastIndexOf("}")
            var json = body.subSequence(start, end + 1).toString()
            return json
        }
        return null
    }

    fun parseRedirect(requestURL: URL, redirectLine: String): ResultHandler? {
        var parts = redirectLine.split(" ")
        if (parts.isNotEmpty() && parts.size > 1) {
            if (parts[1].isBlank()) return null
            val redirect = parts[1]
            if (!redirect.startsWith("http")) { // http & https
                return ResultHandler(URL(requestURL, redirect), null)
            }
            tracer.addDebug(Log.DEBUG, TAG, "Found redirect")
            tracer.addTrace("Found redirect - ${DateUtils.now()} \n")
            return ResultHandler(URL(redirect), null)
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
        private const val PORT_80 = 80
        private const val PORT_443 = 443
    }

        class ResultHandler(redirect: URL?, body: String?) {
        val r: URL? = redirect
        val b: String? = body

        fun getRedirect(): URL? {
            return r
        }

        fun getBody(): String? {
            return b
        }
    }
}
