package id.tru.sdk.network

import android.os.Build
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

class DateUtils {

    /**
     * Returns the ISO-8601 formatted date in UTC, such as '2011-12-03T10:15:30Z'
     */
    fun now(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        } else {
            var simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.ssssss'Z'")
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))
            simpleDateFormat.format(Date())
        }
    }
}