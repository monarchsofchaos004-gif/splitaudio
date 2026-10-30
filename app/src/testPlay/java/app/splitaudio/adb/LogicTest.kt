package app.splitaudio.adb

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class LogicTest {
    // ---- ShellCmd ----
    @Test fun writeFile_roundtrip() {
        val data = ByteArray(10000) { (it * 31 + 7).toByte() }
        val cmds = ShellCmd.writeFileCmds("/data/local/tmp/x.dex", data)
        assertEquals(": > /data/local/tmp/x.dex.b64", cmds.first())
        assertTrue(cmds.last().startsWith("base64 -d /data/local/tmp/x.dex.b64 > /data/local/tmp/x.dex.new && chmod 644 "))
        assertTrue(cmds.last().contains("mv -f /data/local/tmp/x.dex.new /data/local/tmp/x.dex"))
        val parts = cmds.drop(1).dropLast(1)
        val re = Regex("^printf %s '([A-Za-z0-9+/=]+)' >> /data/local/tmp/x\\.dex\\.b64$")
        val b64 = parts.joinToString("") { re.matchEntire(it)!!.groupValues[1] }
        assertArrayEquals(data, Base64.getDecoder().decode(b64))
        assertTrue(cmds.all { it.length < 3500 })
    }
    @Test fun writeFile_emptyAndExactChunk() {
        assertEquals(2, ShellCmd.writeFileCmds("/a/b", ByteArray(0)).size)
        val c = ShellCmd.writeFileCmds("/a/b", ByteArray(2250), chunk = 3000)   // 2250 bytes -> 3000 b64 chars exactly
        assertEquals(3, c.size)
    }
    @Test fun writeFile_rejectsUnsafePath() {
        assertThrows(IllegalArgumentException::class.java) { ShellCmd.writeFileCmds("/a/b; rm -rf /", ByteArray(1)) }
        assertThrows(IllegalArgumentException::class.java) { ShellCmd.writeFileCmds("rel/path", ByteArray(1)) }
        assertThrows(IllegalArgumentException::class.java) { ShellCmd.writeFileCmds("/a/b", ByteArray(1), mode = "644; x") }
    }
    @Test fun quote() { assertEquals("'it'\\''s'", ShellCmd.quote("it's")) }
    @Test fun rcParse() {
        assertEquals(Pair(0, "hello\n"), ShellCmd.parseRc("hello\n__rc=0\n"))
        assertEquals(Pair(127, "x: not found\n"), ShellCmd.parseRc("x: not found\n__rc=127\n"))
        assertEquals(-5, ShellCmd.parseRc("cut off").first)
        assertEquals("a; echo \"__rc=\$?\"", ShellCmd.withRc("a"))
    }

    // ---- SetupLogic ----
    private fun inp(dev: Boolean = false, wifi: Boolean = false, wd: Boolean = false, paired: Boolean = false, sec: Boolean = false, conn: Boolean = false) =
        SetupLogic.Inputs(dev, wifi, wd, paired, sec, conn)

    @Test fun steps_progress() {
        assertEquals(0, SetupLogic.current(inp()))
        assertEquals(1, SetupLogic.current(inp(dev = true)))
        assertEquals(2, SetupLogic.current(inp(dev = true, wd = true, wifi = true)))
        assertEquals(3, SetupLogic.current(inp(dev = true, wd = true, paired = true)))
        assertEquals(-1, SetupLogic.current(inp(dev = true, wd = true, paired = true, sec = true)))
    }
    @Test fun steps_dont_regress_after_pairing() {   // 重開機後無線偵錯被系統關掉，已配對的引導不能退回第 2 步
        assertEquals(-1, SetupLogic.current(inp(dev = false, wd = false, paired = true, sec = true)))
        assertEquals(listOf(true, true, true, false), SetupLogic.steps(inp(paired = true)))
    }
    @Test fun reconnect_policy() {
        val A = SetupLogic.Action.values()
        assertEquals(A[0], SetupLogic.reconnect(inp(wifi = true), false))                       // 沒配對
        assertEquals(A[0], SetupLogic.reconnect(inp(paired = true), false))                     // 沒 WiFi
        assertEquals(A[0], SetupLogic.reconnect(inp(paired = true, wifi = true, conn = true), false))
        assertEquals(A[0], SetupLogic.reconnect(inp(paired = true, wifi = true), true))         // 路由在跑不用 ADB
        assertEquals(SetupLogic.Action.CONNECT, SetupLogic.reconnect(inp(paired = true, wifi = true, wd = true), false))
        assertEquals(SetupLogic.Action.ENABLE_WD_THEN_CONNECT, SetupLogic.reconnect(inp(paired = true, wifi = true, sec = true), false))
        assertEquals(SetupLogic.Action.NOTIFY_ENABLE_WD, SetupLogic.reconnect(inp(paired = true, wifi = true), false))
    }
    @Test fun codes() {
        assertTrue(SetupLogic.validCode("123456")); assertFalse(SetupLogic.validCode("12345")); assertFalse(SetupLogic.validCode("12345a")); assertFalse(SetupLogic.validCode("1234567"))
    }

    // ---- AdbBackend（stdin 暫存檔包裝）----
    @Test fun wrapStdin() {
        val c = AdbBackend.wrapStdin("cat > /data/local/tmp/a.new && mv -f /data/local/tmp/a.new /data/local/tmp/a", "/data/local/tmp/.t")
        assertEquals("( cat > /data/local/tmp/a.new && mv -f /data/local/tmp/a.new /data/local/tmp/a ) < /data/local/tmp/.t; r=\$?; rm -f /data/local/tmp/.t; (exit \$r)", c)
        // withRc 接在後面：用子 shell 結束，外層 shell 不會被 exit 結束掉，__rc 標記才印得出來
        assertTrue(ShellCmd.withRc(c).endsWith("(exit \$r); echo \"__rc=\$?\""))
    }

    // ---- CertGen ----
    @Test fun cert_selfSigned_valid() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val c = CertGen.selfSigned(kp, "SplitAudio Pro")
        c.verify(kp.public)                      // 自簽驗章
        c.checkValidity()
        assertEquals("CN=SplitAudio Pro", c.subjectX500Principal.name)
        assertArrayEquals(kp.public.encoded, c.publicKey.encoded)
        assertEquals("SHA256withRSA", c.sigAlgName)
        assertTrue(c.notAfter.time > System.currentTimeMillis() + 20L * 365 * 86_400_000)
    }
}
