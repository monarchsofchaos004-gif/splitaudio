package app.splitaudio

import org.junit.Assert.*
import org.junit.Test

/** fdroid 版的接線：只用 Shizuku、全部功能免費、有無障礙音量鍵、沒有一次性設定區塊的類別（setupSection 要 Activity，這裡只測常數）。 */
class DistTest {
    @Test fun backends_shizukuOnly() = assertEquals(listOf<Backend>(ShizukuBackend), Dist.backends())
    @Test fun everythingIsFree() = assertTrue(Dist.proAlways)
    @Test fun hasVolumeKeys() = assertTrue(Dist.hasVolKey)
    /** manifest 宣告的是 specialUse（src/fdroid/AndroidManifest.xml）；startForeground 的類型要是它的子集 */
    @Test fun foregroundServiceType_isSpecialUse() = assertEquals(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, Dist.fgsType)

    /** startForeground 的類型一定要是 manifest 宣告類型的子集，不然執行時直接丟例外：兩邊要一致（這個版本的 manifest 是 src/fdroid/AndroidManifest.xml） */
    @Test fun manifestDeclaresTheSameForegroundType() {
        val xml = listOf("src/fdroid/AndroidManifest.xml", "app/src/fdroid/AndroidManifest.xml").map { java.io.File(it) }.first { it.exists() }.readText()
        val m = Regex("<service[^>]*android:name=\"\\.MonitorService\"[^>]*android:foregroundServiceType=\"([A-Za-z|]+)\"").find(xml)
        assertNotNull("MonitorService has no foregroundServiceType in the fdroid manifest", m)
        assertEquals("specialUse", m!!.groupValues[1])
        assertTrue(xml.contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"))
        assertTrue("the fdroid build needs INTERNET for the helper's control port on 127.0.0.1", xml.contains("android.permission.INTERNET\""))
    }

    /** Report/update wiring: the flavor name goes into every issue report; the new-version hint is fdroid only (Google Play updates itself) */
    @Test fun reportAndUpdateWiring() {
        assertEquals("fdroid", Dist.flavor)
        assertEquals(true, Dist.updateCheck)
    }
}
