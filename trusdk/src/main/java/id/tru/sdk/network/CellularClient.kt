package id.tru.sdk.network

import android.content.Context
import android.os.Build
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi
import okhttp3.RequestBody
import org.json.JSONObject

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal class CellularClient(context: Context): ConManager {
    @Throws(java.io.IOException::class)
    override fun requestSync(@NonNull url: String, @NonNull method: String, @Nullable body: RequestBody?): JSONObject? {
        return null
    }
}

interface ConManager {
    @Throws(java.io.IOException::class)
    fun requestSync(@NonNull url: String, @NonNull method: String, @Nullable body: RequestBody? = null): JSONObject?
}