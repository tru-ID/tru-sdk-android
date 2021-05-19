package id.tru.sdk.network

import android.os.Build
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.O) // API Level 26, Oreo, Android 8.0 (61%) coverage
class CelularInfo constructor(telephone: TelephonyManager) {
    private val telephone = telephone

    fun isDataEnabled():Boolean { return telephone.isDataEnabled }

}