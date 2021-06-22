package id.tru.sdk

import android.os.Build
import id.tru.sdk.network.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(RobolectricTestRunner::class)
class DateUtilUnitTest {
    @Test
    fun date_iscorrect() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java,"SDK_INT", 23)
        val version_A = Build.VERSION.SDK_INT
        assertEquals(version_A, 23)
        val dateUtils = DateUtils()
        val date_A = dateUtils.now()
        ReflectionHelpers.setStaticField(Build.VERSION::class.java,"SDK_INT", 26)
        val version_B = Build.VERSION.SDK_INT
        assertEquals(version_B, 26)

        val date_B = dateUtils.now()
        assertEquals(date_A.substring(0,20), date_B.substring(0, 20))
    }
}
