package app.splitaudio

import org.junit.Assert.*
import org.junit.Test

/** Route 的純邏輯：給 approute.sh 的指令字串（帶 uid 和 token）、token 的產生與格式、路由回答的判讀、部署指紋。
 *  跟真的路由程式對話（token／uid 兩關）的測試在 ControlChannelTest（Java，預設套件）。 */
class RouteTest {
    private val tok = "0123456789abcdef0123456789abcdef"

    @Test fun cmd_putsTheAppUidAndTokenInFrontOfEveryScriptCall() {
        assertEquals("SPLITAUDIO_UID=10234 SPLITAUDIO_TOKEN=$tok sh /data/local/tmp/approute.sh start", Route.cmd(10234, tok, "start"))
        assertEquals("SPLITAUDIO_UID=10234 SPLITAUDIO_TOKEN=$tok sh /data/local/tmp/approute.sh multifocus on", Route.cmd(10234, tok, "multifocus on"))
        for (sub in listOf("start", "startmap", "stop", "mixer", "mixstop", "multifocus on", "multifocus off", "players"))
            assertTrue(sub, Route.cmd(1010123, tok, sub).startsWith("SPLITAUDIO_UID=1010123 SPLITAUDIO_TOKEN=$tok sh /data/local/tmp/approute.sh $sub"))
    }

    @Test fun cmd_refusesATokenThatCouldBreakTheShellLine() {
        val bad = listOf("", "abc", tok + "0", tok.substring(1), tok.uppercase(), tok.dropLast(1) + ";", tok.dropLast(1) + " ", "$(id)".padEnd(32, 'a'), tok.dropLast(1) + "\n")
        for (t in bad) assertThrows(t, IllegalArgumentException::class.java) { Route.cmd(1, t, "start") }
    }

    @Test fun newToken_is32LowercaseHexCharacters() {
        repeat(50) {
            val t = Route.newToken()
            assertEquals(32, t.length)
            assertTrue(t, t.all { it in '0'..'9' || it in 'a'..'f' })
            assertTrue(Route.isValidToken(t))
        }
    }

    @Test fun newToken_isNeverTheSameTwice() = assertEquals(200, (1..200).map { Route.newToken() }.toSet().size)

    @Test fun newToken_takesItsBitsFromTheGivenRandom() {
        assertEquals(Route.newToken(java.util.Random(42)), Route.newToken(java.util.Random(42)))
        assertNotEquals(Route.newToken(java.util.Random(42)), Route.newToken(java.util.Random(43)))
        val zero = object : java.util.Random() { override fun nextBytes(b: ByteArray) { b.fill(0) } }
        assertEquals("0".repeat(32), Route.newToken(zero))                     // two hex digits per byte, leading zeros kept
        val ff = object : java.util.Random() { override fun nextBytes(b: ByteArray) { b.fill(-1) } }
        assertEquals("f".repeat(32), Route.newToken(ff))
    }

    @Test fun isValidToken_onlyTheFormatWeMake() {
        assertTrue(Route.isValidToken(tok))
        assertFalse(Route.isValidToken(null)); assertFalse(Route.isValidToken(""))
        assertFalse(Route.isValidToken(tok.substring(1))); assertFalse(Route.isValidToken(tok + "0"))
        assertFalse(Route.isValidToken(tok.uppercase()))
        assertFalse(Route.isValidToken(tok.dropLast(1) + "g"))
        assertFalse(Route.isValidToken(tok.dropLast(1) + "\n"))
    }

    @Test fun portMatchesTheDocumentedOne() = assertEquals(47821, Route.PORT)

    @Test fun answerOf_separatesAnswersFromDenialsAndSilence() {
        assertEquals("on", Route.answerOf("on").reply); assertEquals("", Route.answerOf("on").error)
        assertEquals("legacy", Route.answerOf("legacy").reply)
        assertNull(Route.answerOf("denied").reply); assertEquals(Route.DENIED, Route.answerOf("denied").error)
        assertNull(Route.answerOf(null).reply); assertNotEquals(Route.DENIED, Route.answerOf(null).error)
    }

    @Test fun isAlive_onlyOnOrOff() {
        assertTrue(Route.isAlive("on")); assertTrue(Route.isAlive("off"))
        assertFalse(Route.isAlive(null))
        assertFalse(Route.isAlive(Route.DENIED))   // 別的 token／uid 起的舊路由：不是我們的
        assertFalse(Route.isAlive("err"))          // 沒有 token 協定的舊路由把 auth 那行當指令回 err：不是我們這版的
        assertFalse(Route.isAlive("")); assertFalse(Route.isAlive("map")); assertFalse(Route.isAlive("ON"))
    }

    @Test fun controlProblem_saysWhyTheRouterCannotBeReached() {
        assertEquals("", Route.controlProblem(Route.Answer("on")))
        assertEquals("", Route.controlProblem(Route.Answer("off")))
        assertTrue(Route.controlProblem(Route.answerOf("denied")).contains("denied"))
        assertTrue(Route.controlProblem(Route.answerOf("denied")).contains("Stop split"))
        assertTrue(Route.controlProblem(Route.Answer(null, "failed to connect to /127.0.0.1 (port 47821)")).contains("no answer"))
        assertTrue(Route.controlProblem(Route.Answer(null, "failed to connect to /127.0.0.1 (port 47821)")).contains("47821"))
        assertTrue(Route.controlProblem(Route.answerOf(null)).contains("no answer"))
        assertTrue(Route.controlProblem(Route.Answer("err")).contains("unexpected answer"))
        assertTrue(Route.controlProblem(Route.Answer("err")).contains("err"))
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
