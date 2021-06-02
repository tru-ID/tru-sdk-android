package id.tru.sdk

/**
 * The details of the mobile carrier etc. and available Tru.Id products supported.
 */
data class ReachabilityDetails(
    val error: ReachabilityError?,
    val countryCode: String?,
    val networkId: String?,
    val networkName: String?,
    val products: ArrayList<Product>?,
    val link: String?
)

/**
 * Tru.Id product (API)
 */
data class Product(val productId: String, val productType: ProductType)

/**
 * Types of Tru.Id products
 */
enum class ProductType(val text: String) {
    PhoneCheck("Phone Check"),
    SIMCheck("Sim Check"),
    SubscriberCheck("Subscriber Check"),
    Unknown("Unknown")
}

/**
 * If the isReachable() request is not done via cellular network, this class represents the error.
 */
data class ReachabilityError(
    val type: String?,
    val title: String?,
    val status: Int,
    val detail: String?
)