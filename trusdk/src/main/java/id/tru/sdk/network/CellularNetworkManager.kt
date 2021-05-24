package id.tru.sdk.network

import androidx.annotation.NonNull
import java.net.URL

internal interface CellularNetworkManager {
    fun call(@NonNull url: URL): Boolean
}