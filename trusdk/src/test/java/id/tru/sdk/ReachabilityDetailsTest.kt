package id.tru.sdk

import junit.framework.TestCase
import org.junit.Test

class ReachabilityDetailsTest {

    @Test
    fun toJsonString() {
        val product1 = Product(productId = "Product654", productType = ProductType.SIMCheck)
        val reachabilityDetails = ReachabilityDetails(
            error = ReachabilityError("HTTP", "Redirect", 302, "Some description"),
            countryCode = "GB",
            networkId = "2334",
            networkName = "EE",
            products = arrayListOf(product1),
            link = "_links")

        val expectedValue =
            """{"error":{"type":"HTTP","title":"Redirect","status":302,"detail":"Some description"},"countryCode":"GB","networkId":"2334","networkName":"EE","products":[{"productId":"Product654","productType":"SIMCheck"}],"link":"_links"}"""
        val actualValue = reachabilityDetails.toJsonString()
        TestCase.assertEquals(expectedValue, actualValue)
    }

    @Test
    fun GIVEN_Reachability_Has_Missing_Details_THEN_Test_Should_Not_Fail() {
        val product1 = Product(productId = "Product654", productType = ProductType.SIMCheck)
        val reachabilityDetails = ReachabilityDetails(
            error = ReachabilityError("HTTP", "Redirect", 302, "Some description"),
            countryCode = "",
            networkId = "",
            networkName = "",
            products = arrayListOf(product1),
            link = "_links")

        val expectedValue =
            """{"error":{"type":"HTTP","title":"Redirect","status":302,"detail":"Some description"},"countryCode":"","networkId":"","networkName":"","products":[{"productId":"Product654","productType":"SIMCheck"}],"link":"_links"}"""
        val actualValue = reachabilityDetails.toJsonString()
        TestCase.assertEquals(expectedValue, actualValue)
    }
}
