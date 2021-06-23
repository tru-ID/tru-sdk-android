package id.tru.sdk.network

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class ClientSocketTest {
//        Relative Redirect Example
//        HTTP/1.1 301 Moved Permanently
//        Location: /redirect/1
//        Content-Type: text/html
//        Content-Length: 174

    @Test
    fun parseRedirect_GivenARelativeRedirect_ShouldReturn_AURL_WithRequestHost() {
        //val r = URL("/relative") //Raises Exception
        //val t = URL("https://relative/1") //No exceptions, relative is treated as host.
        val requestURL = URL("https://www.tru.id/check")
        val responseLocationLine = "Location: /redirect/1"
        val expectedRedirectURL = URL("https://www.tru.id/redirect/1")

        val clientSocket = ClientSocket()
        val redirectURL = clientSocket.parseRedirect(requestURL, responseLocationLine)
        assertEquals(redirectURL, expectedRedirectURL)
    }

    @Test
    fun parseRedirect_GivenARegularRedirectURLWithLowerUpperCaseCharacters_ShouldReturn_Itself() {

        val requestURL = URL("https://www.tru.id/check")
        val responseLocationLine = "Location: https://www.cnn.com/interaction/ExlouINc"
        val expectedRedirectURL = URL("https://www.cnn.com/interaction/ExlouINc")

        val clientSocket = ClientSocket()
        val redirectURL = clientSocket.parseRedirect(requestURL, responseLocationLine)
        assertEquals(redirectURL, expectedRedirectURL)
    }

    @Test
    fun parseRedirect_GivenARegularRedirectURL_ShouldReturn_Itself() {

        val requestURL = URL("https://www.tru.id/check")
        val responseLocationLine = "Location: https://www.cnn.com/redirect/1"
        val expectedRedirectURL = URL("https://www.cnn.com/redirect/1")

        val clientSocket = ClientSocket()
        val redirectURL = clientSocket.parseRedirect(requestURL, responseLocationLine)
        assertEquals(redirectURL, expectedRedirectURL)
    }

    @Test
    fun parseRedirect_GivenEmptyline_ShouldReturn_Null() {

        val requestURL = URL("https://www.tru.id/check")
        val responseLocationLine = "Location: "

        val clientSocket = ClientSocket()
        val redirectURL = clientSocket.parseRedirect(requestURL, responseLocationLine)
        assertEquals(redirectURL, null)
    }
}