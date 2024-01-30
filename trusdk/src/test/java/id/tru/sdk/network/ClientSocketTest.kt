package id.tru.sdk.network

import org.junit.Assert.*

import org.junit.Test
import java.net.URL

class ClientSocketTest {

    @Test
    fun makePost_WithStandardValues_NoInjection() {
        val expectedString =
            "POST /testing_path HTTP/1.1" +
                    "\r\nHost: example.com" +
                    "\r\nx-tru-mode: sandbox" +
                    "\r\nUser-Agent: tru.id.sdk" +
                    "\r\nAccept: text/html,application/xhtml+xml,application/xml,*/*" +
                    "\r\nContent-Length: 0"+
                    "\r\nConnection: close\r\n\r\n"

        val cs = ClientSocket()

        val url = URL("https://example.com/testing_path")
        val headers = mapOf("x-tru-mode" to "sandbox",
            "User-Agent" to "tru.id.sdk",
            "Accept" to "text/html,application/xhtml+xml,application/xml,*/*")
        val actualString = cs.makePost(url = url, headers= headers, body=null)
        assertEquals(expectedString, actualString)
    }

    @Test
    fun makePost_WithStandardValues_WithCRLFInjection() {
        val expectedString =
            "POST /testing_path HTTP/1.1" +
                    "\r\nHost: example.com" +
                    "\r\nx-tru-mode: sandbox" +
                    "\r\nUser-Agent: tru.id.sdk" +
                    "\r\nFoo: barmy_injected_property: Injection" +
                    "\r\nAccept: text/html,application/xhtml+xml,application/xml,*/*" +
                    "\r\nContent-Length: 0"+
                    "\r\nConnection: close\r\n\r\n"

        val cs = ClientSocket()

        val url = URL("https://example.com/testing_path")
        val headers = mapOf("x-tru-mode" to "sandbox",
            "User-Agent" to "tru.id.sdk",
            "Foo" to "\nbar\r\nmy_injected_\rproperty: Injection\r\n",
            "Accept" to "text/html,application/xhtml+xml,application/xml,*/*")
        val actualString = cs.makePost(url = url, headers= headers, body=null)
        assertEquals(expectedString, actualString)
    }

    @Test
    fun makePost_WithOnlyCRLFValues_WithCRLFInjection() {
        val expectedString =
            "POST /testing_path HTTP/1.1" +
                    "\r\nHost: example.com" +
                    "\r\nx-tru-mode: sandbox" +
                    "\r\nUser-Agent: tru.id.sdk" +
                    "\r\nFoo: " +
                    "\r\nAccept: text/html,application/xhtml+xml,application/xml,*/*" +
                    "\r\nContent-Length: 0"+
                    "\r\nConnection: close\r\n\r\n"

        val cs = ClientSocket()

        val url = URL("https://example.com/testing_path")
        val headers = mapOf("x-tru-mode" to "sandbox",
            "User-Agent" to "tru.id.sdk",
            "Foo" to "\r\n",
            "Accept" to "text/html,application/xhtml+xml,application/xml,*/*")
        val actualString = cs.makePost(url = url, headers= headers, body=null)
        assertEquals(expectedString, actualString)
    }

    @Test
    fun makePost_EscapedCRLFValues_WithCRLFInjection() {
        val expectedString =
            "POST /testing_path HTTP/1.1" +
                    "\r\nHost: example.com" +
                    "\r\nx-tru-mode: sandbox" +
                    "\r\nUser-Agent: tru.id.sdk" +
                    "\r\nFoo: \\n\bar\\rmy_injected_property: Injection" +
                    "\r\nAccept: text/html,application/xhtml+xml,application/xml,*/*" +
                    "\r\nContent-Length: 0"+
                    "\r\nConnection: close\r\n\r\n"

        val cs = ClientSocket()

        val url = URL("https://example.com/testing_path")
        val headers = mapOf("x-tru-mode" to "sandbox",
            "User-Agent" to "tru.id.sdk",
            "Foo" to "\\n\bar\\r\nmy_injected_\rproperty: Injection\r\n",
            "Accept" to "text/html,application/xhtml+xml,application/xml,*/*")
        val actualString = cs.makePost(url = url, headers= headers, body=null)
        assertEquals(expectedString, actualString)
    }

    @Test
    fun makePost_LongStringCRLFValues_WithCRLFInjection() {
        val expectedString =
            "POST /testing_path HTTP/1.1" +
                    "\r\nHost: example.com" +
                    "\r\nx-tru-mode: sandbox" +
                    "\r\nUser-Agent: tru.id.sdk" +
                    "\r\nFoo: barmy_injected_property: InjectionVeryLong String Example Is this long enough" +
                    "\r\nAccept: text/html,application/xhtml+xml,application/xml,*/*" +
                    "\r\nContent-Length: 0"+
                    "\r\nConnection: close\r\n\r\n"

        val cs = ClientSocket()

        val url = URL("https://example.com/testing_path")
        val headers = mapOf("x-tru-mode" to "sandbox",
            "User-Agent" to "tru.id.sdk",
            "Foo" to "\n\rbar\r\nmy_injected_\rproperty: Injection\r\nVeryLong String Example Is this \r\nlong enough\n",
            "Accept" to "text/html,application/xhtml+xml,application/xml,*/*")
        val actualString = cs.makePost(url = url, headers= headers, body=null)
        assertEquals(expectedString, actualString)
    }

    @Test
    fun makePost_UrlCheckWithCRLFInjection() {
        val expectedString =
            "POST /testing_pathInjectedHeader: value\\rAnotherHeader: value HTTP/1.1" +
                    "\r\nHost: example.com" +
                    "\r\nx-tru-mode: sandbox" +
                    "\r\nUser-Agent: tru.id.sdk" +
                    "\r\nFoo: barmy_injected_property: Injection" +
                    "\r\nAccept: text/html,application/xhtml+xml,application/xml,*/*" +
                    "\r\nContent-Length: 0"+
                    "\r\nConnection: close\r\n\r\n"

        val cs = ClientSocket()

        val url = URL("https://example.com/testing_path\nInjectedHeader: value\\r\nAnotherHeader: value")
        val headers = mapOf("x-tru-mode" to "sandbox",
            "User-Agent" to "tru.id.sdk",
            "Foo" to "\n\rbar\r\nmy_injected_\rproperty: Injection\r\n",
            "Accept" to "text/html,application/xhtml+xml,application/xml,*/*")
        val actualString = cs.makePost(url = url, headers= headers, body=null)
        assertEquals(expectedString, actualString)
    }
}