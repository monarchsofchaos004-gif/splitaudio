package app.splitaudio

import org.junit.Assert.*
import org.junit.Test

/** Route 的純邏輯：給 approute.sh 的指令字串（帶 uid）、路由回答的判讀、部署指紋。 */
class RouteTest {
    @Test fun cmd_putsTheAppUidInFrontOfEveryScriptCall() {
        assertEquals("SPLITAUDIO_UID=10234 sh /data/local/tmp/approute.sh start", Route.cmd(10234, "start"))
        assertEquals("SPLITAUDIO_UID=10234 sh /data/local/tmp/approute.sh multifocus on", Route.cmd(10234, "multifocus on"))
        for (sub in listOf("start", "startmap", "stop", "mixer", "mixstop", "multifocus on", "multifocus off", "players"))
            assertTrue(sub, Route.cmd(1010123, sub).startsWith("SPLITAUDIO_UID=1010123 sh /data/local/tmp/approute.sh $sub"))
    }

    @Test fun sh_alwaysCarriesTheVariable() = assertTrue(Route.sh("stop").startsWith("SPLITAUDIO_UID="))

    @Test fun isAlive_anyAnswerButDenied() {
        assertTrue(Route.isAlive("on")); assertTrue(Route.isAlive("off")); assertTrue(Route.isAlive("map"))
        assertFalse(Route.isAlive(null))
        assertFalse(Route.isAlive(Route.DENIED))   // 別的 uid 起的舊路由：不是我們的
    }

    @Test fun fingerprint_stableAndSensitive() {
        val a = listOf("approute.dex" to byteArrayOf(1, 2, 3), "approute.sh" to "echo hi".toByteArray())
        val f = Route.fingerprint(a)
        assertEquals(64, f.length)
        assertTrue(f.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(f, Route.fingerprint(a))                                                                   // 同樣內容＝同樣指紋
        assertNotEquals(f, Route.fingerprint(listOf("approute.dex" to byteArrayOf(1, 2, 4), a[1])))              // dex 變一個 byte
        assertNotEquals(f, Route.fingerprint(listOf(a[0], "approute.sh" to "echo ho".toByteArray())))            // sh 變
        assertNotEquals(f, Route.fingerprint(listOf("approute.dex" to a[1].second, "approute.sh" to a[0].second))) // 兩檔內容對調
        assertNotEquals(f, Route.fingerprint(listOf(a[0])))                                                      // 少一個檔
    }

    @Test fun fingerprint_boundaryBetweenFilesIsNotAmbiguous() {
        // 前一個檔的尾巴／後一個檔的頭挪一個 byte，不能撞成同一個指紋
        val x = Route.fingerprint(listOf("a" to byteArrayOf(1, 2), "b" to byteArrayOf(3)))
        val y = Route.fingerprint(listOf("a" to byteArrayOf(1), "b" to byteArrayOf(2, 3)))
        assertNotEquals(x, y)
    }

    @Test fun sameVersion_comparesTrimmed() {
        assertTrue(Route.sameVersion("abc123\n", "abc123"))
        assertFalse(Route.sameVersion("abc124", "abc123"))
        assertFalse(Route.sameVersion("", "abc123"))      // 手機上沒有 approute.ver（舊版／被清掉）→ 重新部署
        assertFalse(Route.sameVersion(null, "abc123"))
    }

    @Test fun hasPid_scriptOutput() {
        assertTrue(Route.hasPid("started pid=1234\n10-30 12:00:00 control socket"))
        assertTrue(Route.hasPid("already pid=77"))
        assertFalse(Route.hasPid("started pid=\n"))       // 兩秒內就掛了
        assertFalse(Route.hasPid("empty map"))
        assertFalse(Route.hasPid(""))
    }
}
