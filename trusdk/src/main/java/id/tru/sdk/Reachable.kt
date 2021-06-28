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