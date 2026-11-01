package app.splitaudio

import app.splitaudio.adb.AdbBackend
import org.junit.Assert.*
import org.junit.Test

/** play 版的接線：Shizuku 排前面（有就用）、其次內建 ADB；Pro 要買或試用；沒有無障礙音量鍵。 */
class DistTest {
    @Test fun backends_shizukuFirstThenAdb() = assertEquals(listOf<Backend>(ShizukuBackend, AdbBackend), Dist.backends())
    @Test fun proIsGated() = assertFalse(Dist.proAlways)
    @Test fun noVolumeKeys() = assertFalse(Dist.hasVolKey)
    /** manifest 宣告的是 connectedDevice（src/play/AndroidManifest.xml，MonitorService 和 PairService） */
    @Test fun foregroundServiceType_isConnectedDevice() = assertEquals(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE, Dist.fgsType)

    /** startForeground 的類型一定要是 manifest 宣告類型的子集，不然執行時直接丟例外：兩個前景服務的宣告都要跟 Dist.fgsType 一致（src/play/AndroidManifest.xml） */
    @Test fun manifestDeclaresTheSameForegroundType() {
        val xml = listOf("src/play/AndroidManifest.xml", "app/src/play/AndroidManifest.xml").map { java.io.File(it) }.first { it.exists() }.readText()
        for (service in listOf("\\.MonitorService", "\\.adb\\.PairService")) {
            val m = Regex("<service[^>]*android:name=\"$service\"[^>]*android:foregroundServiceType=\"([A-Za-z|]+)\"").find(xml)
            assertNotNull("$service has no foregroundServiceType in the play manifest", m)
            assertEquals("connectedDevice", m!!.groupValues[1])
        }
        assertTrue(xml.contains("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"))
        assertTrue(xml.contains("android.permission.CHANGE_WIFI_MULTICAST_STATE"))   // connectedDevice 在 Android 14+ 需要的前提權限之一
        assertTrue("the built-in ADB needs INTERNET", xml.contains("android.permission.INTERNET\""))
    }

    /** Report/update wiring: the flavor name goes into every issue report; the new-version hint is fdroid only (Google Play updates itself) */
    @Test fun reportAndUpdateWiring() {
        assertEquals("play", Dist.flavor)
        assertEquals(false, Dist.updateCheck)
    }
}
