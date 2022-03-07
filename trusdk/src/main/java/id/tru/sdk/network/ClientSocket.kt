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
    fun check(url: URL, operator: String?): JSONObject? {
        var redirectURL: URL? = null
        var redirectCount = 0
        var result: ResultHandler? = null
        do {
            redirectCount += 1
            val nurl = redirectURL ?: url
            tracer.addDebug(Log.DEBUG, TAG, "Requesting: $nurl")
            if (startConnection(nurl)) {
                if (redirectCount == 1)
                    result = sendCommand(nurl, operator, null)
                else
                    result = sendCommand(nurl, null, result?.getCookies())
                if (result != null && result.getRedirect() != null)
                    redirectURL = result.getRedirect()
                else
                    redirectURL = null
                stopConnection()
            } else
                tracer.addDebug(Log.DEBUG, TAG, "Cannot start connection: $nurl")
        } while (redirectURL != null && redirectCount <= MAX_REDIRECT_COUNT)
        tracer.addDebug(Log.DEBUG, TAG, "Check complete")
        if (result?.getBody() != null) {
            try {
                return JSONObject(result.getBody())
            } catch (e: JSONException) {
                tracer.addDebug(Log.ERROR, TAG, "JSONException: $e")
            }
        }
        return null
    }

    fun getJSON(url: URL, operator: String?): JSONObject? {
        var result: ResultHandler?
        tracer.addDebug(Log.DEBUG, TAG, "Requesting: $url")
        if (startConnection(url)) {
            result = sendCommand(url, operator, null)
            stopConnection()
            if (result?.getBody() != null) {
                try {
                    return JSONObject(result.getBody())
                } catch (e: JSONException) {
                    tracer.addDebug(Log.ERROR, TAG, "JSONException: $e")
                }
            }
        } else
            tracer.addDebug(Log.DEBUG, TAG, "Cannot start connection: $url")
        return null
    }

    private fun makeHTTPCommand(url: URL, operator: String?, cookies: ArrayList<String>?): String {
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
        if (operator != null)
            cmd.append("x-tru-ops: ${operator}$CRLF")
        if (isEmulator())
            cmd.append("x-tru-mode: sandbox$CRLF")
        cmd.append("Accept: text/html,application/xhtml+xml,application/xml,*/*$CRLF")
        var cs = StringBuffer()
        val iterator = cookies.orEmpty().listIterator()
        for (cookie in iterator) {
            cs.append(cookie)
            if (iterator.hasNext()) cs.append("; ")
        }
        if (cs.length>1) cmd.append("Cookie: "+cs.toString()+"$CRLF")

        cmd.append("Connection: close$CRLF$CRLF")
        return cmd.toString()
    }

    private fun sendCommand(url: URL, operator: String?, cookies: ArrayList<String>?): ResultHandler? {
        val command = makeHTTPCommand(url, operator, cookies)
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

    private fun startConnection(url: URL) : Boolean {
        var port = PORT_80
        if (url.port > 0) port = url.port
        tracer.addDebug(Log.DEBUG, TAG, "start : ${url.host} ${url.port} ${url.protocol}")
        tracer.addTrace("\nStart connection ${url.host} ${url.port} ${url.protocol} ${DateUtils.now()}\n")
        try {
            socket = if (url.protocol == "https") {
                port = PORT_443
                if (url.port > 0) port = url.port
                SSLSocketFactory.getDefault().createSocket(url.host, port)
            } else {
                Socket(url.host, port)
            }
            tracer.addDebug(Log.DEBUG, TAG, "Client created : ${socket.inetAddress.hostAddress} ${socket.port}")
            socket.soTimeout = 5*1000
            output = socket.getOutputStream()
            input = BufferedReader(InputStreamReader(socket.inputStream))

            tracer.addDebug(Log.DEBUG, TAG, "Client connected : ${socket.inetAddress.hostAddress} ${socket.port}")
            tracer.addTrace("Connected ${DateUtils.now()}\n")
            return true
        } catch (ex: Exception) {
            tracer.addDebug(Log.DEBUG, TAG, "Client exception : ${ex.message}")
            tracer.addTrace("Client exception ${ex.message}\n")
            socket.close()
            return false
        }
    }

    private fun sendAndReceive(requestURL: URL, message: String): ResultHandler? {
        tracer.addDebug(Log.DEBUG, TAG, "Client sending \n$message\n")
        tracer.addTrace(message)
        try {
            val bytesOfRequest: ByteArray =
                    message.toByteArray(Charset.forName(StandardCharsets.UTF_8.name()))
            output.write(bytesOfRequest)
            output.flush()
        } catch (ex: Exception) {
            tracer.addDebug(Log.DEBUG, TAG, "Client sending exception : ${ex.message}")
            tracer.addTrace("Client sending exception ${ex.message}\n")
        }
        tracer.addDebug(Log.DEBUG, TAG, "Response " + "\n")
        tracer.addTrace("Response - ${DateUtils.now()} \n")
        var status: Int = 0
        var body: String? = null
        var type: String = ""
        var result: ResultHandler? = null
        var bodyBegin: Boolean = false
        var cookies: ArrayList<String> = ArrayList<String>()

        try {
            // convert the entire stream in a String
            var response: String? = IOUtils.toString(input)
            response?.let {
                val lines = response.split("\n");
                for (line in lines) {
                    tracer.addDebug(Log.DEBUG, TAG, line)
                    tracer.addTrace(line)
                    if (line.startsWith("HTTP/")) {
                        val parts = line.split(" ")
                        if (parts.isNotEmpty() && parts.size >= 2) {
                            status = Integer.valueOf(parts[1])
                            tracer.addDebug(Log.DEBUG, TAG, "Status - $status")
                            tracer.addTrace("Status - $status ${DateUtils.now()}\n")
                            if (status == 200) {
                                continue
                            } else if (status < 300 || status > 310) {
                                break
                            }
                        }
                    } else if (line.contains("Set-Cookie:") || line.contains("set-cookie:")) {
                        val parts: List<String> = line.split(" ")
                        if (!parts.isEmpty() && parts.size>1) {
                            var cookie: String = parts[1]
                            cookie = cookie.replace(";","")
                            cookies.add(cookie)
                            tracer.addDebug(Log.DEBUG, TAG, "cookie - $cookie")
                            tracer.addTrace("cookie - $cookie\n")
                        }
                    } else if (line.contains("Location:") || line.contains("location:")) {
                        result = parseRedirect(requestURL, line.replace("\r",""), cookies)
                    } else if (line.contains("Content-Type:")) {
                        var parts = line.split(" ")
                        if (!parts.isEmpty() && parts.size>1) {
                            type = parts[1].replace(";","").replace("\r","")
                        }
                        tracer.addDebug(Log.DEBUG, TAG, "Type - $type\n")
                    } else if (status == 200 && ("application/json".equals(type) || "application/hal+json".equals(type)) && line.equals("\r")) {
                        bodyBegin = true
                    } else if ( bodyBegin ) {
                        body = if (body!=null) body + line.replace("\r","") else line.replace("\r","")
                        tracer.addDebug(Log.DEBUG, TAG, "Adding to body - $body\n")
                    }
                }
                tracer.addDebug(Log.DEBUG, TAG, "Status - $status\nBody - $body\n")
                tracer.addTrace("Status - $status ${DateUtils.now()}\nBody - $body\n")
                if (status == 200 && body != "") {
                    result = ResultHandler(null, parseBodyIntoJSONString(body), cookies)
                }
            }
        } catch (ex: Exception) {
            tracer.addDebug(Log.DEBUG, TAG, "Client reading exception : ${ex.message}")
            tracer.addTrace("Client reading exception ${ex.message}\n")
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

    fun parseRedirect(requestURL: URL, redirectLine: String, cookies: ArrayList<String>?): ResultHandler? {
        tracer.addDebug(Log.DEBUG, TAG, "parseRedirect : ${redirectLine}")
        var parts = redirectLine.split("ocation: ")
        if (parts.isNotEmpty() && parts.size > 1) {
            if (parts[1].isBlank()) return null
            val redirect = parts[1]
            // some location header are not properly encoded
            var cleanRedirect = redirect.replace(" ", "+")
            tracer.addDebug(Log.DEBUG, TAG, "cleanRedirect : ${cleanRedirect}")
            if (!cleanRedirect.startsWith("http")) { // http & https
                return ResultHandler(URL(requestURL, cleanRedirect), null, cookies)
            }
            tracer.addDebug(Log.DEBUG, TAG, "Found redirect")
            tracer.addTrace("Found redirect - ${DateUtils.now()} \n")
            return ResultHandler(URL(cleanRedirect), null, cookies)
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

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.contains("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.PRODUCT.contains("sdk_gphone_x86");
    }

    companion object {
        private const val TAG = "CellularClient"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val MAX_REDIRECT_COUNT = 10
        private const val PORT_80 = 80
        private const val PORT_443 = 443
    }

    class ResultHandler(redirect: URL?, body: String?, cookies: ArrayList<String>?) {
        val r: URL? = redirect
        val b: String? = body
        val cs: ArrayList<String>? = cookies

        fun getRedirect(): URL? {
            return r
        }

        fun getBody(): String? {
            return b
        }

        fun getCookies(): ArrayList<String>? {
            return cs
        }
    }
}
