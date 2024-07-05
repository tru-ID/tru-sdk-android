/*
 * MIT License
 * Copyright (C) 2022 4Auth Limited. All rights reserved
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.IOException
import java.io.OutputStream
import java.net.HttpCookie
import java.net.Socket
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.net.ssl.SSLSocketFactory
import org.json.JSONException
import org.json.JSONObject

internal class ClientSocket constructor(var tracer: TraceCollector = TraceCollector.instance) {
    private lateinit var socket: Socket
    private lateinit var output: OutputStream
    private lateinit var input: BufferedReader

    fun open(url: URL, accessToken: String?, operator: String?): JSONObject {
        if (url.protocol != "https") {
            return convertError("invalid scheme", "Only HTTPS URLs are allowed")
        }
        val requestId: String = UUID.randomUUID().toString()
        var redirectURL: URL? = null
        var redirectCount = 0
        var result: ResultHandler? = null
        do {
            redirectCount += 1
            val nurl = redirectURL ?: url
            tracer.addDebug(Log.DEBUG, TAG, "Requesting: $nurl")
            try {
                startConnection(nurl)
                if (redirectCount == 1)
                    result = sendCommand(nurl, accessToken, operator, null, requestId)
                else
                    result = sendCommand(nurl, null, null, result?.getCookies(), requestId)
                if (result != null && result.getRedirect() != null)
                    redirectURL = result.getRedirect()
                else
                    redirectURL = null
                stopConnection()
            } catch (ex: Exception) {
                tracer.addDebug(Log.DEBUG, TAG, "Cannot start connection: $nurl")
                return convertError("sdk_connection_error", "ex: ".plus(ex.localizedMessage))
            }
        } while (redirectURL != null && redirectCount <= MAX_REDIRECT_COUNT)
        if (redirectCount == MAX_REDIRECT_COUNT)
            return convertError("sdk_redirect_error", "Too many redirects")
        tracer.addDebug(Log.DEBUG, TAG, "Open completed")
        if (result != null)
            return convertResultHandler(result)
        return convertError("sdk_error", "internal error")
    }

    private fun convertResultHandler(res: ResultResponse): JSONObject {
        var json: JSONObject = JSONObject()
        json.put("http_status", res.getHttpStatus())
        try {

            if (res.getBody() != null)
                json.put("response_body", JSONObject(res.getBody()))
            return json
        } catch (e: JSONException) {
            if (res.getBody() != null)
                json.put("response_raw_body", res.getBody())
            else
                return convertError("sdk_error", "ex: ".plus(e.localizedMessage))
        }
        return json
    }

    private fun convertError(code: String, description: String): JSONObject {
        var json: JSONObject = JSONObject()
        json.put("error", code)
        json.put("error_description", description)
        return json
    }
    private fun makeHTTPCommand(url: URL, accessToken: String?, operator: String?, cookies: ArrayList<HttpCookie>?, requestId: String?): String {
        val sanitizedUrlString = sanitizeInput(url.toString())
        val sanitizedUrl = URL(sanitizedUrlString)
        val sanitizedHost = sanitizeInput(sanitizedUrl.host)
        val cmd = StringBuffer()
        cmd.append("GET " + sanitizedUrl.path)
        if (sanitizedUrl.path.isEmpty()) {
            cmd.append("/")
        }
        if (sanitizedUrl.query != null) {
            cmd.append("?" + sanitizedUrl.query)
        }
        cmd.append(" HTTP/1.1$CRLF")
        cmd.append("Host: $sanitizedHost")
        if (sanitizedUrl.protocol == "https" && sanitizedUrl.port > 0 && sanitizedUrl.port != PORT_443) {
            cmd.append(":" + sanitizedUrl.port)
        } else if (sanitizedUrl.protocol == "http" && sanitizedUrl.port > 0 && sanitizedUrl.port != PORT_80) {
            cmd.append(":" + sanitizedUrl.port)
        }
        cmd.append(CRLF)
        val userAgent = userAgent()
        cmd.append("$HEADER_USER_AGENT: $userAgent$CRLF")
        if (accessToken != null)
            cmd.append("Authorization: Bearer ${accessToken}$CRLF")
        if (requestId != null)
            cmd.append("x-tru-sdk-request: ${requestId}$CRLF")
        if (operator != null)
            cmd.append("x-tru-ops: ${operator}$CRLF")
        if (isEmulator())
            cmd.append("x-tru-mode: sandbox$CRLF")

        cmd.append("Accept: text/html,application/xhtml+xml,application/xml,*/*$CRLF")
        var cs = StringBuffer()
        var cookieCount = 0
        val iterator = cookies.orEmpty().listIterator()
        for (cookie in iterator) {
            if (((cookie.secure && sanitizedUrl.protocol == "https") || (!cookie.secure)) &&
                (cookie.domain == null || (cookie.domain != null && sanitizedUrl.host.contains(cookie.domain))) &&
                (cookie.path == null || sanitizedUrl.path.startsWith(cookie.path))) {
                if (cookieCount > 0) cs.append("; ")
                cs.append(cookie.name + "=" + cookie.value)
                cookieCount++
            }
        }
        if (cs.length > 1) cmd.append("Cookie: " + cs.toString() + "$CRLF")

        cmd.append("Connection: close$CRLF$CRLF")
        return cmd.toString()
    }

    private fun sanitizeInput(input: String): String {
        // Remove newline characters
        return input.replace("\r", "").replace("\n", "")
    }

    private fun sendCommand(url: URL, accessToken: String?, operator: String?, cookies: ArrayList<HttpCookie>?, requestId: String?): ResultHandler? {
        val command = makeHTTPCommand(url, accessToken, operator, cookies, requestId)
        return sendAndReceive(url, command, cookies)
    }

    private fun startConnection(url: URL) {
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
        } catch (ex: Exception) {
            tracer.addDebug(Log.ERROR, TAG, "Cannot create socket exception : ${ex.message}")
            tracer.addTrace("Cannot create socket exception ${ex.message}\n")
            throw ex
        }
        return try {
            tracer.addDebug(Log.DEBUG, TAG, "Client created : ${socket.inetAddress.hostAddress} ${socket.port}")
            socket.soTimeout = 5 * 1000
            output = socket.getOutputStream()
            input = BufferedReader(InputStreamReader(socket.inputStream))
            tracer.addDebug(Log.DEBUG, TAG, "Client connected : ${socket.inetAddress.hostAddress} ${socket.port}")
            tracer.addTrace("Connected ${DateUtils.now()}\n")
        } catch (ex: Exception) {
            tracer.addDebug(Log.ERROR, TAG, "Client exception : ${ex.message}")
            tracer.addTrace("Client exception ${ex.message}\n")
            if (!socket.isClosed) socket.close()
            throw ex
        }
    }

    private fun sendAndReceive(requestURL: URL, message: String, existingCookies: ArrayList<HttpCookie>?): ResultHandler? {
        tracer.addDebug(Log.DEBUG, TAG, "Client sending \n$message\n")
        tracer.addTrace(message)
        try {
            val bytesOfRequest: ByteArray =
                    message.toByteArray(Charset.forName(StandardCharsets.UTF_8.name()))
            output.write(bytesOfRequest)
            output.flush()
        } catch (ex: Exception) {
            tracer.addDebug(Log.ERROR, TAG, "Client sending exception : ${ex.message}")
            tracer.addTrace("Client sending exception ${ex.message}\n")
            throw ex
        }
        tracer.addDebug(Log.DEBUG, TAG, "Response " + "\n")
        tracer.addTrace("Response - ${DateUtils.now()} \n")
        var status: Int = 0
        var body: String? = null
        var type: String = ""
        var result: ResultHandler? = null
        var bodyBegin: Boolean = false
        var cookies: ArrayList<HttpCookie> = ArrayList<HttpCookie>()
        if (existingCookies != null) cookies.addAll(existingCookies)

        try {
            // convert the entire stream in a String
            var response: String? = readMultipleChars(input, 65536)
            response?.let {
                val lines = response.split("\n")
                for (line in lines) {
                    tracer.addDebug(Log.DEBUG, TAG, line)
                    tracer.addTrace(line)
                    if (line.startsWith("HTTP/")) {
                        val parts = line.split(" ")
                        if (parts.isNotEmpty() && parts.size >= 2) {
                            status = Integer.valueOf(parts[1].trim())
                            tracer.addDebug(Log.DEBUG, TAG, "Status - $status")
                            tracer.addTrace("Status - $status ${DateUtils.now()}\n")
                        }
                    } else if (line.contains("Set-Cookie:") || line.contains("set-cookie:")) {
                        val parts: List<String> = line.split("ookie:")
                        if (!parts.isEmpty() && parts.size > 1) {
                            try {
                                for (cookie in HttpCookie.parse(parts[1])) {
                                    cookies.add(cookie)
                                    tracer.addDebug(Log.DEBUG, TAG, "cookie - $cookie")
                                    tracer.addTrace("cookie - $cookie\n")
                                }
                            } catch (ex: IllegalArgumentException) {
                                tracer.addTrace("Cannot parse cookie ${parts[1]}  ${ex.message}\n")
                            }
                        }
                    } else if (line.contains("Location:") || line.contains("location:")) {
                        result = parseRedirect(status, requestURL, line.replace("\r", ""), cookies)
                    } else if (line.contains("Content-Type:")) {
                        var parts = line.split(" ")
                        if (!parts.isEmpty() && parts.size > 1) {
                            type = parts[1].replace(";", "").replace("\r", "")
                        }
                        tracer.addDebug(Log.DEBUG, TAG, "Type - $type\n")
                    } else if (("application/json".equals(type) || "application/hal+json".equals(type) || "application/problem+json".equals(type)) && line.equals("\r")) {
                        bodyBegin = true
                    } else if (bodyBegin) {
                        body = if (body != null) body + line.replace("\r", "") else line.replace("\r", "")
                        tracer.addDebug(Log.DEBUG, TAG, "Adding to body - $body\n")
                    }
                }
                tracer.addDebug(Log.DEBUG, TAG, "Status - $status\nBody - $body\n")
                tracer.addTrace("Status - $status ${DateUtils.now()}\nBody - $body\n")
                result = if (result != null)
                    return result
                else if (bodyBegin && body != null) {
                    ResultHandler(status, null, parseBodyIntoJSONString(body), cookies)
                } else
                    ResultHandler(status, null, null, null)
            }
        } catch (ex: Exception) {
            tracer.addDebug(Log.ERROR, TAG, "Client reading exception : ${ex.message}")
            tracer.addTrace("Client reading exception ${ex.message}\n")
            throw ex
        }
        return result
    }
    @Throws(IOException::class)
    private fun readMultipleChars(reader: BufferedReader, length: Int): String? {
        val chars = CharArray(length)
        val charsRead = reader.read(chars, 0, length)
        val result: String = if (charsRead != -1) {
            String(chars, 0, charsRead)
        } else {
            ""
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

    fun parseRedirect(httpStatus: Int, requestURL: URL, redirectLine: String, cookies: ArrayList<HttpCookie>?): ResultHandler? {
        tracer.addDebug(Log.DEBUG, TAG, "parseRedirect : $redirectLine")
        var parts = redirectLine.split("ocation: ")
        if (parts.isNotEmpty() && parts.size > 1) {
            if (parts[1].isBlank()) return null
            val redirect = parts[1]
            // some location header are not properly encoded
            var cleanRedirect = redirect.replace(" ", "+")
            tracer.addDebug(Log.DEBUG, TAG, "cleanRedirect : $cleanRedirect")
            if (!cleanRedirect.startsWith("http")) { // http & https
                return ResultHandler(httpStatus, URL(requestURL, cleanRedirect), null, cookies)
            }
            tracer.addDebug(Log.DEBUG, TAG, "Found redirect")
            tracer.addTrace("Found redirect - ${DateUtils.now()} \n")
            return ResultHandler(httpStatus, URL(cleanRedirect), null, cookies)
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
        return Build.FINGERPRINT.contains("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            Build.PRODUCT.contains("sdk_gphone_x86")
    }

    companion object {
        private const val TAG = "CellularClient"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val MAX_REDIRECT_COUNT = 10
        private const val PORT_80 = 80
        private const val PORT_443 = 443
        private const val CRLF = "\r\n"
    }

    class ResultHandler(httpStatus: Int, redirect: URL?, body: String?, cookies: ArrayList<HttpCookie>?) : ResultResponse {
        val s: Int = httpStatus
        val r: URL? = redirect
        val b: String? = body
        val cs: ArrayList<HttpCookie>? = cookies

        override fun getHttpStatus(): Int {
            return s
        }

        fun getRedirect(): URL? {
            return r
        }

        override fun getBody(): String? {
            return b
        }

        fun getCookies(): ArrayList<HttpCookie>? {
            return cs
        }
    }

    class ResponseHandler(httpStatus: Int, body: String?) : ResultResponse {
        val s: Int = httpStatus
        val b: String? = body

        override fun getHttpStatus(): Int {
            return s
        }

        override fun getBody(): String? {
            return b
        }
    }

    internal interface ResultResponse {
        fun getHttpStatus(): Int

        fun getBody(): String?
    }
}
