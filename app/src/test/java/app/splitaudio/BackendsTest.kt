package app.splitaudio

import org.junit.Assert.*
import org.junit.Test

/** 後端選擇邏輯（Backends.pick）與 Backend.writeFile 預設實作，用假後端測。 */
class BackendsTest {
    private class Fake(override val name: String, private val granted: Boolean, private val boom: Boolean = false) : Backend {
        var lastCmd: String? = null
        var lastStdin: ByteArray? = null
        override fun alive() = granted
        override fun granted(): Boolean { if (boom) throw IllegalStateException("binder died"); return granted }
        override fun run(cmd: String, timeoutSec: Int, stdin: ByteArray?): Pair<Int, String> { lastCmd = cmd; lastStdin = stdin; return Pair(0, name) }
    }

    @Test fun emptyList_noBackend() = assertNull(Backends.pick(emptyList()))

    @Test fun noneGranted_noBackend() = assertNull(Backends.pick(listOf(Fake("shizuku", false), Fake("adb", false))))

    @Test fun firstGrantedWins_inListOrder() {
        val shz = Fake("shizuku", true); val adb = Fake("adb", true)
        assertSame(shz, Backends.pick(listOf(shz, adb)))     // 兩個都能用 → 清單排前面的（Shizuku）
        assertSame(adb, Backends.pick(listOf(adb, shz)))
    }

    @Test fun skipsBackendsThatAreNotGranted() {
        val shz = Fake("shizuku", false); val adb = Fake("adb", true)
        assertSame(adb, Backends.pick(listOf(shz, adb)))     // Shizuku 沒授權 → 走內建 ADB
    }

    @Test fun throwingBackendCountsAsNotGranted() {
        val shz = Fake("shizuku", true, boom = true); val adb = Fake("adb", true)
        assertSame(adb, Backends.pick(listOf(shz, adb)))
        assertNull(Backends.pick(listOf(shz)))
    }

    @Test fun defaultWriteFile_goesThroughStdin() {
        val b = Fake("shizuku", true)
        val data = byteArrayOf(1, 2, 3)
        val r = b.writeFile("/data/local/tmp/x.dex", data)
        assertEquals(Pair(0, "shizuku"), r)
        assertEquals("cat > /data/local/tmp/x.dex.new && chmod 644 /data/local/tmp/x.dex.new && mv -f /data/local/tmp/x.dex.new /data/local/tmp/x.dex", b.lastCmd)
        assertArrayEquals(data, b.lastStdin)
    }

    @Test fun defaultWriteFile_modeIsPassedThrough() {
        val b = Fake("shizuku", true)
        b.writeFile("/data/local/tmp/a.sh", ByteArray(0), mode = "755")
        assertTrue(b.lastCmd!!.contains("chmod 755 /data/local/tmp/a.sh.new"))
    }
}
