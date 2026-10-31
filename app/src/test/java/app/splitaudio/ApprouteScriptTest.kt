package app.splitaudio

import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

/** approute.sh 在這台電腦上乾跑：腳本複本裡的 /data/local/tmp 換成暫存資料夾，pm／pgrep／app_process／setsid／nohup 換成放在 PATH 前面的假指令
 *  （真的 /data/local/tmp 和手機都不碰）。驗：mickeep on／off／state 的旗標檔與輸出、start 之後 log 檔的權限（呼叫端 umask 000 也要是 0644，不是 0666）、
 *  token 檔 0600、start 照常算出路由程式的參數。沒有 /bin/sh 或檔案系統不支援 POSIX 權限的環境整組略過。 */
class ApprouteScriptTest {
    private lateinit var root: File
    private lateinit var tmp: File      // 取代手機上的 /data/local/tmp
    private lateinit var bin: File      // 假指令
    private lateinit var script: File   // 換過資料夾的 approute.sh 複本
    private val tok = "0123456789abcdef0123456789abcdef"

    @Before fun setUp() {
        assumeTrue("needs a POSIX shell", File("/bin/sh").exists())
        root = Files.createTempDirectory("approute-dry").toFile()
        assumeTrue("needs POSIX file permissions", Files.getFileStore(root.toPath()).supportsFileAttributeView("posix"))
        tmp = File(root, "tmp").apply { mkdirs() }
        bin = File(root, "bin").apply { mkdirs() }
        fake("pm", "echo \"package:com.google.android.apps.youtube.music uid:10101\"\n" +
            "echo \"package:app.morphe.android.apps.youtube.music uid:10102\"\n" +
            "echo \"package:com.example.video uid:10200\"")
        fake("pgrep", "exit 1")                                // 沒有路由在跑
        fake("app_process", "echo \"fake router \$*\"")        // 假的路由程式：把收到的參數寫進 log 就結束
        fake("appops", "echo \"\$*\" >> ${tmp.path}/appops.calls")
        fake("setsid", "exec \"\$@\"")
        fake("nohup", "exec \"\$@\"")
        val src = listOf("src/main/assets/approute.sh", "app/src/main/assets/approute.sh").map(::File).first { it.exists() }.readText()
        assertTrue("approute.sh no longer has the D=/data/local/tmp line this test swaps", src.contains("D=/data/local/tmp; LOG=\$D/approute.log"))
        val swapped = src.replace("D=/data/local/tmp; ", "D=${tmp.path}; ")
        assertFalse("the dry run must never touch the real /data/local/tmp", swapped.contains("D=/data/local/tmp"))
        script = File(root, "approute.sh").apply { writeText(swapped) }
    }

    @After fun tearDown() { if (::root.isInitialized) root.deleteRecursively() }

    private fun fake(name: String, body: String) {
        val f = File(bin, name)
        f.writeText("#!/bin/sh\n$body\n")
        assertTrue(f.setExecutable(true))
    }

    /** 跑 approute.sh <args>。呼叫端先 umask 000（最寬鬆，才看得出腳本自己有沒有把權限收緊）；回傳合併後的輸出 */
    private fun sh(vararg args: String): String {
        val pb = ProcessBuilder(listOf("/bin/sh", "-c", "umask 000; exec /bin/sh \"\$0\" \"\$@\"", script.path) + args)
        pb.redirectErrorStream(true)
        val env = pb.environment()
        env["PATH"] = bin.path + File.pathSeparator + (System.getenv("PATH") ?: "/usr/bin:/bin")
        env["SPLITAUDIO_UID"] = "10234"; env["SPLITAUDIO_TOKEN"] = tok
        val p = pb.start()
        val out = p.inputStream.bufferedReader().readText()
        assertTrue("approute.sh ${args.joinToString(" ")} did not finish", p.waitFor(30, TimeUnit.SECONDS))
        return out
    }

    private fun perms(f: File): String = PosixFilePermissions.toString(Files.getPosixFilePermissions(f.toPath()))

    @Test fun mickeep_offMakesTheFlagFile_onRemovesIt_stateSaysWhichIsSet() {
        val flag = File(tmp, "approute.nomickeep")
        assertEquals("mickeep=on", sh("mickeep", "state").trim())       // 沒有旗標檔＝開（預設）
        assertEquals("mickeep=on", sh("mickeep").trim())                // 沒給參數＝只回報現況
        assertFalse(flag.exists())
        assertEquals("mickeep=off", sh("mickeep", "off").trim())
        assertTrue(flag.exists())
        assertEquals("rw-r--r--", perms(flag))                          // 呼叫端 umask 000 也是 0644
        assertEquals("mickeep=off", sh("mickeep", "state").trim())
        assertEquals("mickeep=off", sh("mickeep", "off").trim())        // 重複關也行
        assertEquals("mickeep=on", sh("mickeep", "on").trim())
        assertFalse(flag.exists())
        assertEquals("mickeep=on", sh("mickeep", "on").trim())          // 重複開也行
        assertEquals("mickeep=on", sh("mickeep", "nonsense").trim())    // 不認得的參數＝只回報現況，不動旗標
        assertFalse(flag.exists())
    }

    @Test fun mickeep_stateFollowsAFlagFileMadeByHand() {
        File(tmp, "approute.nomickeep").writeText("")
        assertEquals("mickeep=off", sh("mickeep", "state").trim())
    }

    @Test fun start_logAndTokenFilesAreNotWorldWritable_andTheRouterGetsItsArguments() {
        val out = sh("start")
        assertTrue(out, out.contains("started pid="))
        val log = File(tmp, "approute.log")
        assertEquals("rw-r--r--", perms(log))                           // 呼叫端 umask 000：以前這裡是 rw-rw-rw-
        val token = File(tmp, "approute.token")
        assertEquals("rw-------", perms(token))                         // token 檔自己收到 0600，不受 umask 022 影響
        assertEquals(tok, token.readText())
        val logText = log.readText()
        assertTrue(logText, logText.contains("AppRoute 10101,10102 10200"))   // 先是音樂 App 的 uid，再是其他 App 的 uid
    }

    private fun appopsCalls(): List<String> = File(tmp, "appops.calls").takeIf { it.exists() }?.readLines().orEmpty()

    @Test fun nofocus_startmapAppliesTheList_dropsRemovedOnes_stopRevertsAll() {
        File(tmp, "approute.pkgmap").writeText("speaker:com.example.video\n")
        File(tmp, "approute.nofocus").writeText("com.example.video\nbad;name\n")
        sh("startmap")
        assertEquals(listOf("set com.example.video TAKE_AUDIO_FOCUS ignore"), appopsCalls())
        assertEquals("com.example.video", File(tmp, "approute.nofocus.applied").readText().trim())
        File(tmp, "approute.nofocus").writeText("com.example.other\n")   // 換一個
        sh("startmap")
        assertEquals(listOf("set com.example.video TAKE_AUDIO_FOCUS ignore", "set com.example.video TAKE_AUDIO_FOCUS default",
            "set com.example.other TAKE_AUDIO_FOCUS ignore"), appopsCalls())
        sh("stop")
        assertEquals("set com.example.other TAKE_AUDIO_FOCUS default", appopsCalls().last())
        assertFalse(File(tmp, "approute.nofocus.applied").exists())
    }
}
