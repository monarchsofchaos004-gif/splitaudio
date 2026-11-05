package app.splitaudio

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.widget.Toast
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** 狀態＋設定頁（字串在 res/values*，英文預設、繁中 values-zh-rTW）。
 *  上：（Play 版）一次性設定＝內建 ADB 引導；狀態卡（分流中／等音樂／沒在跑，顏色跟著變）＋啟動／停止；
 *  ① 必要設定（fdroid 版）：Shizuku、音量鍵（無障礙）；
 *  ② 客製化：簡單／自訂模式、（簡單模式）選音樂 App、（自訂模式）每個 App 選輸出、每個 App 的音量、開影片不打斷音樂、按麥克風時不暫停音樂；
 *  第一次分流時如果用戶還沒決定「開影片會不會暫停音樂」，上面多一張決定卡（開／不要，見 [Mf]）。
 *  兩個版本的差異都收在 [Dist]（後端清單、Pro 是否一律開放、有沒有音量鍵、設定區塊）。 */
class MainActivity : Activity() {
    private lateinit var liveCard: LinearLayout
    private lateinit var tvLive: TextView
    private lateinit var tvLiveSub: TextView
    private lateinit var tvLast: TextView
    private lateinit var tvBackend: TextView
    private lateinit var cusCard: LinearLayout
    private lateinit var shzChip: TextView
    private lateinit var shzBtn: Button
    private lateinit var accChip: TextView
    private lateinit var accBtn: Button
    private lateinit var segSimple: Button
    private lateinit var segCustom: Button
    private lateinit var appsBtn: Button
    private lateinit var musicBtn: Button
    private lateinit var mfCard: LinearLayout
    private lateinit var mfSw: Switch
    private lateinit var tvMf: TextView
    private lateinit var mkSw: Switch
    private lateinit var tvMk: TextView
    private lateinit var tvModeDesc: TextView
    private lateinit var tvPro: TextView
    private lateinit var updCard: LinearLayout
    private lateinit var tvUpd: TextView
    private lateinit var fbCard: LinearLayout
    private lateinit var fbTop: LinearLayout
    private lateinit var fbBottom: LinearLayout
    private lateinit var tvFb: TextView
    private lateinit var tvFbLast: TextView
    private var btnFbClose: View? = null
    private lateinit var failCard: LinearLayout
    private lateinit var crashCard: LinearLayout
    private var updTag: String? = null
    private var updUrl: String? = null
    private var setup: SetupSection? = null
    @Volatile private var mf: String? = null
    private var mfBusy = false
    @Volatile private var mk: String? = null   // 按麥克風時不暫停音樂："on"／"off"／null（讀不到，當成預設的開）
    private var mkBusy = false
    private val ui = Handler(Looper.getMainLooper())
    private var ticking = false
    private val tickRun = object : Runnable {
        override fun run() { if (ticking) { refreshSoon(); ui.postDelayed(this, 3000) } }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        MonitorService.ensure(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(20), dp(16), dp(24)) }

        // 標題卡
        val hero = card(0).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(C_PURPLE, C_PINK)).apply { cornerRadius = dp(22).toFloat() }
        }
        hero.addView(text("🎧 " + getString(R.string.app_name), 26f, Color.WHITE, true))
        hero.addView(text(getString(R.string.hero_sub), 14f, Color.rgb(252, 228, 236)).apply { setPadding(0, dp(6), 0, 0) })
        root.addView(hero)

        // 新版本提示（每天最多查一次；fdroid 版只連到 GitHub 發布頁、不下載安裝；play 版整個關掉）
        updCard = card(Color.WHITE).apply {
            visibility = View.GONE
            tvUpd = text("", 16f, C_TEXT, true).apply { setPadding(0, 0, 0, dp(4)) }
            addView(tvUpd)
            addView(btn(getString(R.string.upd_whatsnew), C_BLUE) { updUrl?.let { openUrl(it) } })
            // 0.5.5 免費版：沒有「這版先不提醒」，卡片一直提示到更新為止（Dist.feedbackDismissable）
            if (Dist.feedbackDismissable) addView(btn(getString(R.string.upd_skip), C_GREY) { updTag?.let { Update.skip(this@MainActivity, it) }; renderTop() })
        }
        root.addView(updCard)
        // 0.5.6：「能用嗎？」卡與底下的「回報與意見」合併成同一張卡（用戶 2026-11-04：兩個區塊太像）。
        // 第一次分流順利跑約 10 分鐘、還沒回答時，這張卡搬到這個頂端槽提醒一次；回答過（能用！／回報問題／Play 版關閉）就回到底下的槽，
        // 「回報問題」「複製診斷文字」一直都在。卡片本體在下面 fbCard 建，renderTop() 決定放哪個槽。
        fbTop = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(fbTop)
        // 偵測到失敗 → 一鍵帶診斷回報；免費版沒有「關閉」（分流又正常或 24 小時後卡片自己消失）
        failCard = card(Color.WHITE).apply {
            visibility = View.GONE
            addView(text("⚠ " + getString(R.string.fail_title), 16f, C_RED, true).apply { setPadding(0, 0, 0, dp(4)) })
            addView(text(getString(if (Dist.feedbackDismissable) R.string.fail_hint else R.string.fail_hint_auto), 13f, C_GREY).apply { setPadding(0, 0, 0, dp(6)) })
            addView(btn(getString(R.string.fail_report), C_RED) { reportDialog("problem") })
            if (Dist.feedbackDismissable) addView(btn(getString(R.string.btn_close), C_GREY) { Health.dismissFailure(this@MainActivity); renderTop() })
        }
        root.addView(failCard)
        // 0.5.4：上次 App 當掉（Crash 存了堆疊）→ 一鍵附當機記錄回報；回報後同一次當機不再問；免費版沒有「關閉」（7 天後自己消失）
        crashCard = card(Color.WHITE).apply {
            visibility = View.GONE
            addView(text("⚠ " + getString(R.string.crash_title), 16f, C_RED, true).apply { setPadding(0, 0, 0, dp(4)) })
            addView(text(getString(R.string.crash_hint), 13f, C_GREY).apply { setPadding(0, 0, 0, dp(6)) })
            addView(btn(getString(R.string.fail_report), C_RED) { reportDialog("crash") })
            if (Dist.feedbackDismissable) addView(btn(getString(R.string.btn_close), C_GREY) { Crash.dismiss(this@MainActivity); renderTop() })
        }
        root.addView(crashCard)

        // Play 版：一次性設定（內建 ADB 引導）；fdroid 版沒有（null），改用下面「必要設定」的 Shizuku 那列
        setup = Dist.setupSection(this, root)

        // 狀態卡（顏色跟著狀態）＋手動啟動／停止
        liveCard = card(C_GREY)
        tvLive = text("…", 20f, Color.WHITE, true)
        tvLiveSub = text("", 13f, Color.WHITE).apply { setPadding(0, dp(4), 0, dp(6)) }
        liveCard.addView(tvLive); liveCard.addView(tvLiveSub)
        liveCard.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(btn("▶ " + getString(R.string.btn_start), Color.WHITE, 1f, C_GREEN) { Thread { Route.start(this@MainActivity) }.start() })
            addView(btn("■ " + getString(R.string.btn_stop), Color.WHITE, 1f, C_RED) { Thread { Route.stop(this@MainActivity) }.start() })
        })
        tvLast = text("", 11f, Color.argb(220, 255, 255, 255)).apply { setPadding(dp(4), dp(2), 0, 0) }
        liveCard.addView(tvLast)
        tvBackend = text("", 11f, Color.argb(220, 255, 255, 255)).apply { setPadding(dp(4), dp(2), 0, 0); visibility = if (setup != null) View.VISIBLE else View.GONE }
        liveCard.addView(tvBackend)
        root.addView(liveCard)

        // 「開影片會不會暫停音樂？」第一次先問：用戶沒決定過、系統設定又是關的才顯示（決定後卡片消失，下面的開關還能再改）
        mfCard = card(Color.WHITE).apply {
            visibility = View.GONE
            addView(section("🎬 " + getString(R.string.mf_ask_title), C_ORANGE))
            addView(text(getString(R.string.mf_ask_desc), 13f, C_TEXT_2).apply { setPadding(0, 0, 0, dp(6)) })
            addView(btn(getString(R.string.mf_ask_on), C_GREEN) { applyMf(true) })
            addView(btn(getString(R.string.mf_ask_off), C_GREY) { applyMf(false) })
        }
        root.addView(mfCard)

        // ① 必要設定：Shizuku（fdroid 版）、音量鍵（有 VolKeyService 的版本）
        if (setup == null || Dist.hasVolKey) {
            val req = card(Color.WHITE)
            req.addView(section(getString(R.string.sec_required), C_TEAL))
            if (setup == null) {
                shzChip = chip(); shzBtn = btn(getString(R.string.btn_grant), C_ORANGE) { ShizukuBackend.request() }
                req.addView(row("🔑", getString(R.string.row_shizuku), getString(R.string.row_shizuku_d), shzChip, shzBtn))
            }
            if (setup == null && Dist.hasVolKey) req.addView(divider())
            if (Dist.hasVolKey) {
                accChip = chip(); accBtn = btn(getString(R.string.btn_open), C_BLUE) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                req.addView(row("🔊", getString(R.string.row_acc), getString(R.string.row_acc_d), accChip, accBtn))
            }
            root.addView(req)
        }

        // ② 客製化
        cusCard = card(Color.WHITE)
        cusCard.addView(section(getString(R.string.sec_custom), C_PURPLE))
        cusCard.addView(text(getString(R.string.mode_title), 15f, C_TEXT, true))
        segSimple = btn(getString(R.string.mode_simple), C_TEAL, 1f) { setMode(false) }
        segCustom = btn(getString(R.string.mode_custom), C_PURPLE, 1f) { if (requirePro()) setMode(true) }
        cusCard.addView(LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(segSimple); addView(segCustom) })
        tvModeDesc = text("", 13f, C_TEXT_2).apply { setPadding(dp(4), 0, dp(4), dp(4)) }
        cusCard.addView(tvModeDesc)
        musicBtn = btn("🎵 " + getString(R.string.btn_music, 0), C_TEAL) { startActivity(Intent(this, MusicActivity::class.java)) }
        cusCard.addView(musicBtn)
        appsBtn = btn("🎛️ " + getString(R.string.btn_apps), C_INDIGO) { if (requirePro()) startActivity(Intent(this, AppsActivity::class.java)) }
        cusCard.addView(appsBtn)
        cusCard.addView(btn("🔉 " + getString(R.string.btn_mixer), C_PINK) { if (requirePro()) startActivity(Intent(this, MixerActivity::class.java)) })
        tvPro = text("", 12f, C_TEXT_2).apply { setPadding(dp(4), 0, dp(4), dp(2)); visibility = View.GONE }
        cusCard.addView(tvPro)
        cusCard.addView(divider())
        mfSw = Switch(this).apply {
            text = "🎬 " + getString(R.string.mf_title); textSize = 15f; setTextColor(C_TEXT); setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(2))
            setOnCheckedChangeListener { _, on ->
                if (mfBusy || on == (mf == "on")) return@setOnCheckedChangeListener
                applyMf(on)
            }
        }
        cusCard.addView(mfSw)
        tvMf = text("", 13f, C_TEXT_2).apply { setPadding(0, 0, 0, dp(4)) }
        cusCard.addView(tvMf)
        // 按麥克風（語音輸入／助理）時不暫停音樂：路由程式看到別的 App 搶走聲音就立刻替音樂按播放。預設開，要先開上面的多重焦點才有用
        mkSw = Switch(this).apply {
            text = "🎤 " + getString(R.string.mic_keep_title); textSize = 15f; setTextColor(C_TEXT); setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(2))
            setOnCheckedChangeListener { _, on ->
                if (mkBusy || on == (mk != "off")) return@setOnCheckedChangeListener
                applyMicKeep(on)
            }
        }
        cusCard.addView(mkSw)
        tvMk = text("", 13f, C_TEXT_2).apply { setPadding(0, 0, 0, dp(4)) }
        cusCard.addView(tvMk)
        root.addView(cusCard)

        // 回報與意見（唯一一張回報卡）：標題問「在你的 <機型> 能用嗎？」；「能用！」「回報問題」開預填好的 GitHub 表單（只要按送出），
        // 「複製診斷文字」給沒有 GitHub 帳號的人貼給我們；回報過能用後標題改成謝謝、「能用！」收起。免費版沒有「關閉」（兩條路都收資料）。
        fbBottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(fbBottom)
        // 0.5.6 一鍵直送（用戶選 A）：「能用！」直接把診斷送到自家接口（server/），「回報問題」先問一句發生什麼事再送；送不出去改開 GitHub 表單。
        // 按鈕一直都在（用戶：不要回報過就永遠收起）；「上次回報」一行讓他知道送過什麼。
        fbCard = card(Color.WHITE).apply {
            addView(section("💬 " + getString(R.string.sec_help), C_BLUE))
            tvFb = text("", 16f, C_TEXT, true).apply { setPadding(0, 0, 0, dp(4)) }
            addView(tvFb)
            addView(text(getString(R.string.help_desc), 13f, C_TEXT_2).apply { setPadding(0, 0, 0, dp(6)) })
            tvFbLast = text("", 13f, C_TEXT_2).apply { setPadding(0, 0, 0, dp(6)); visibility = View.GONE }
            addView(tvFbLast)
            addView(btn(getString(R.string.btn_works), C_GREEN) { Health.answered(this@MainActivity, Diag.versionCode(this@MainActivity)); sendReport("works", ""); renderTop() })
            addView(btn(getString(R.string.btn_report), C_RED) { Health.answered(this@MainActivity, Diag.versionCode(this@MainActivity)); reportDialog("problem"); renderTop() })
            addView(btn(getString(R.string.btn_copy_diag), C_GREY) { copyDiag() })
            if (Dist.feedbackDismissable) btnFbClose = btn(getString(R.string.btn_close), C_GREY) { Health.answered(this@MainActivity, Diag.versionCode(this@MainActivity)); renderTop() }.also { addView(it) }
        }
        if (Dist.updateCheck) {
            val about = card(Color.WHITE)
            // 0.5.5 免費版：新版檢查沒有開關（只說明它做什麼）；付費版保留開關
            if (Dist.feedbackDismissable) about.addView(Switch(this).apply {
                text = getString(R.string.upd_switch); textSize = 15f; setTextColor(C_TEXT); setTypeface(typeface, Typeface.BOLD)
                isChecked = Update.enabled(this@MainActivity)
                setOnCheckedChangeListener { _, on -> Update.setEnabled(this@MainActivity, on); if (on) checkUpdate(); renderTop() }
            })
            else about.addView(text(getString(R.string.upd_switch), 15f, C_TEXT, true))
            about.addView(text(getString(R.string.upd_switch_d), 13f, C_TEXT_2).apply { setPadding(0, 0, 0, dp(4)) })
            root.addView(about)
        }

        setup?.addFooter(root)

        val scroll = ScrollView(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(C_BG_TOP, C_BG_BOTTOM))
            addView(root)
        }
        setContentView(scroll)
        Edge.apply(scroll, root)
    }

    override fun onResume() {
        super.onResume()
        setup?.let { s ->
            s.onResume { refreshSoon() }
            // 一次性設定的每一步要自動偵測（使用者去設定頁回來就會自己打勾）→ 前景時每 3 秒查一次；fdroid 版不輪詢
            ticking = true; ui.removeCallbacks(tickRun); ui.postDelayed(tickRun, 3000)
        }
        refreshSoon()
        renderTop()
        checkUpdate()
    }

    private fun checkUpdate() { Update.checkAsync(this) { runOnUiThread { renderTop() } } }

    /** 頂端三張卡（新版本／第一次成功／失敗）的顯示與文字 */
    internal fun renderTop() {
        val u = Update.pending(this)
        updTag = u?.tag; updUrl = u?.htmlUrl
        updCard.visibility = if (u != null) View.VISIBLE else View.GONE
        if (u != null) tvUpd.text = "🆕 " + getString(R.string.upd_title, Update.parseVersion(u.tag)?.toString() ?: u.tag)
        // 回報卡：換版或滿 30 天先重新武裝提問；第一次順利 10 分鐘、還沒回答 → 搬到頂端槽；否則在底下槽。按鈕一直都在。
        Health.reaskIfDue(this, Diag.versionCode(this))
        val top = Health.successCardVisible(this)
        val slot = if (top) fbTop else fbBottom
        if (fbCard.parent !== slot) { (fbCard.parent as? LinearLayout)?.removeView(fbCard); slot.addView(fbCard) }
        tvFb.text = (if (top) "🎉 " else "") + getString(R.string.succ_title, Diag.deviceLabel())
        val last = Feedback.lastReport(this)
        tvFbLast.visibility = if (last == null) View.GONE else View.VISIBLE
        if (last != null) {
            val kind = getString(when (last.first) { "works" -> R.string.fb_kind_works; "crash" -> R.string.fb_kind_crash; else -> R.string.fb_kind_problem })
            tvFbLast.text = getString(R.string.fb_last, kind, last.second, java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(last.third)))
        }
        btnFbClose?.visibility = if (top) View.VISIBLE else View.GONE
        failCard.visibility = if (Health.failCardVisible(this)) View.VISIBLE else View.GONE
        crashCard.visibility = if (Crash.cardVisible(this)) View.VISIBLE else View.GONE
    }

    /** 用瀏覽器開網址（沒有瀏覽器就提示） */
    private fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { Toast.makeText(this, R.string.toast_no_browser, Toast.LENGTH_LONG).show() }
    }

    private fun copyDiag() {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("SplitAudio diagnostics", Diag.text(this)))
        Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
    }

    /** 0.5.6: the GitHub form for a kind of report (fallback when the direct send fails, or when the user wants a thread to follow) */
    private fun formUrl(kind: String): String = when (kind) { "works" -> Diag.worksUrl(this); "crash" -> Diag.crashUrl(this); else -> Diag.problemUrl(this) }

    /** One-tap direct send; when it fails, the pre-filled GitHub form opens so nobody is left without a way to report. */
    private fun sendReport(kind: String, note: String) {
        if (kind == "crash") Crash.dismiss(this)
        val unconfirmed = kind == "works" && !Health.recentlyOn(this)   // 0.5.7: the split was not seen on lately = the report is saved as "to be confirmed"
        Feedback.send(this, kind, note) { ok ->
            if (ok && unconfirmed && !isFinishing && !isDestroyed) AlertDialog.Builder(this).setMessage(R.string.fb_unconfirmed).setPositiveButton(android.R.string.ok, null).show()
            else Toast.makeText(applicationContext, if (ok) R.string.toast_sent else R.string.toast_send_fail, Toast.LENGTH_LONG).show()
            if (!ok) openUrl(formUrl(kind))
            renderTop()
        }
    }

    /** "Report a problem" / crash card: one optional line about what happened, then Send (direct) or the GitHub form. */
    private fun reportDialog(kind: String) {
        val input = EditText(this).apply {
            hint = getString(R.string.fb_note_hint); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2; maxLines = 5; setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle(getString(if (kind == "crash") R.string.crash_title else R.string.fail_title))
            .setView(input)
            .setPositiveButton(R.string.fb_send) { _, _ -> sendReport(kind, input.text.toString()) }
            .setNeutralButton(R.string.fb_github) { _, _ -> if (kind == "crash") Crash.dismiss(this); openUrl(formUrl(kind)); renderTop() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        ticking = false; ui.removeCallbacks(tickRun)
        setup?.onPause()
    }

    private fun setMode(custom: Boolean) {
        if (custom == Outputs.customFlag(this)) return
        Outputs.setCustom(this, custom); val c = applicationContext
        Thread { Route.apply(c) }.start()
        if (custom && !Outputs.hasAny(this)) startActivity(Intent(this, AppsActivity::class.java))
        refreshSoon()
    }

    /** Pro 功能（自訂模式、每個 App 的音量）的閘：未解鎖就跳說明，不進頁面。回 true＝可以繼續。 */
    private fun requirePro(): Boolean {
        if (Pro.unlocked(this)) return true
        val left = Pro.trialDaysLeft(this)
        AlertDialog.Builder(this)
            .setTitle(if (left > 0) getString(R.string.pro_title_left, left) else getString(R.string.pro_title_over))
            .setMessage(R.string.pro_msg).setPositiveButton(android.R.string.ok, null).show()
        return false
    }

    /** 開／關「開影片不打斷音樂」（背景執行緒）：成功就把用戶的決定記下來——開＝同意（之後分流會自動打開）、關＝不要（不會再自動打開） */
    private fun applyMf(on: Boolean) {
        val c = applicationContext
        Thread {
            val r = Route.setMultiFocus(c, on)
            Route.lastStart = getString(R.string.mf_result, r)
            if (r.startsWith("multifocus=")) Mf.setDecision(c, Mf.decisionFor(on))
            refreshSoon()
        }.start()
    }

    /** 開／關「按麥克風時不暫停音樂」（背景執行緒）：路由程式每次要用都查旗標檔，所以馬上生效，不用重啟路由 */
    private fun applyMicKeep(on: Boolean) {
        val c = applicationContext
        Thread {
            val r = Route.setMicKeep(c, on)
            Route.lastStart = getString(R.string.mic_keep_result, r)
            refreshSoon()
        }.start()
    }

    internal fun refreshSoon() {
        Thread {
            Thread.sleep(600)
            val acc = Dist.hasVolKey && (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").contains("$packageName/")
            val s = Route.ask(this, "state")
            val be = Backends.current()
            val shzAlive = ShizukuBackend.alive(); val shzGranted = ShizukuBackend.granted()
            val conn = if (be == null) getString(R.string.v_none) else be.name + " " + getString(if (be.alive()) R.string.v_connected else R.string.v_disconnected)
            val up = be != null && be.alive()   // 連著才去問（內建 ADB 已配對但斷線時，不要每次重新整理都去試著連線）
            mf = if (up) Route.multiFocusState() else null
            mk = if (up) Route.micKeepState() else null
            val mfNow = Mf.reconcile(Mf.decision(this), mf)   // 系統設定早就開著（舊版開的）或舊版的關閉鈕按過 → 不用再問
            if (mfNow != Mf.decision(this)) Mf.setDecision(this, mfNow)
            val nMusic = Music.set(this).count { packageManager.getLaunchIntentForPackage(it) != null }
            val custom = Outputs.customFlag(this); val nApps = Outputs.all(this).size
            val proLeft = if (Dist.proAlways || Pro.licensed(this)) -1 else Pro.trialDaysLeft(this)
            val upd = setup?.poll()
            runOnUiThread {
                upd?.invoke()
                renderTop()
                // 有一次性設定區塊的版本：設定完成（有可用的後端）才顯示分流功能；fdroid 版一直顯示
                val ready = setup == null || be != null
                liveCard.visibility = if (ready) View.VISIBLE else View.GONE
                cusCard.visibility = if (ready) View.VISIBLE else View.GONE

                val (live, color) = when (s) {
                    "on" -> Pair("🟢 " + getString(R.string.live_on), C_GREEN)
                    "off" -> Pair("🔵 " + getString(R.string.live_wait), C_BLUE)
                    else -> Pair("⚪ " + getString(R.string.live_none), C_GREY)
                }
                tvLive.text = live
                (liveCard.background as GradientDrawable).setColor(color)
                tvLiveSub.text = if (custom && nApps > 0) getString(R.string.st_mode_custom, nApps) else getString(R.string.st_mode_simple)
                tvLast.text = getString(R.string.st_last, Route.lastStart)
                tvBackend.text = getString(R.string.st_conn, conn)

                if (setup == null) {
                    setChip(shzChip, if (shzGranted) 1 else 0, getString(if (!shzAlive) R.string.v_not_running else if (shzGranted) R.string.v_granted else R.string.v_not_granted))
                    shzBtn.visibility = if (shzGranted) View.GONE else View.VISIBLE
                }
                if (Dist.hasVolKey) {
                    if (Build.VERSION.SDK_INT < 33) {   // 音量鍵要讀系統的 VolumeInfo（Android 13 起才有）
                        setChip(accChip, 3, getString(R.string.chip_needs_13)); accBtn.visibility = View.GONE
                    } else {
                        setChip(accChip, if (acc) 1 else 2, getString(if (acc) R.string.v_on else R.string.chip_recommended))
                        accBtn.visibility = if (acc) View.GONE else View.VISIBLE
                    }
                }

                segPaint(segSimple, !custom, C_TEAL); segPaint(segCustom, custom, C_PURPLE)
                tvModeDesc.text = getString(if (custom && nApps == 0) R.string.mode_custom_empty else if (custom) R.string.mode_custom_d else R.string.mode_simple_d)
                appsBtn.visibility = if (custom) View.VISIBLE else View.GONE
                musicBtn.visibility = if (custom && nApps > 0) View.GONE else View.VISIBLE   // 自訂模式（真的有設定 App）不用音樂 App 清單
                musicBtn.text = "🎵 " + getString(R.string.btn_music, nMusic)
                tvPro.visibility = if (proLeft < 0) View.GONE else View.VISIBLE
                if (proLeft >= 0) tvPro.text = if (proLeft > 0) getString(R.string.pro_trial_left, proLeft) else getString(R.string.pro_trial_over)

                mfBusy = true; mfSw.isChecked = mf == "on"; mfSw.isEnabled = up; mfBusy = false
                mfCard.visibility = if (ready && up && Mf.needsAsk(mfNow, mf)) View.VISIBLE else View.GONE
                tvMf.text = when (mf) { "on" -> getString(R.string.mf_desc); "off" -> getString(R.string.mf_off_auto); "off_user" -> getString(R.string.mf_off_user); else -> getString(R.string.mf_desc) }
                mkBusy = true; mkSw.isChecked = mk != "off"; mkSw.isEnabled = up; mkBusy = false
                tvMk.text = getString(when {
                    mk == "off" -> R.string.mic_keep_off_d
                    mf == "off" || mf == "off_user" -> R.string.mic_keep_needs_mf   // 多重焦點沒開：打開這個也不會生效，說清楚
                    else -> R.string.mic_keep_on_d
                })
            }
        }.start()
    }

    // ---- 小元件（internal：Play 版的設定區塊也用） ----
    internal fun card(bg: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(bg) }
        elevation = dp(3).toFloat()
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
    }

    internal fun text(t: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    internal fun section(t: String, color: Int): TextView = text(t, 19f, color, true).apply { setPadding(0, 0, 0, dp(10)) }

    internal fun divider() = View(this).apply {
        setBackgroundColor(Color.rgb(236, 239, 241))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { setMargins(0, dp(10), 0, dp(10)) }
    }

    /** 一列：圖示＋標題／說明＋狀態膠囊（右）＋按鈕（下，做完就藏起來） */
    private fun row(icon: String, title: String, desc: String, chip: TextView, action: Button): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(text(icon, 24f, C_TEXT), LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(title, 16f, C_TEXT, true))
                addView(text(desc, 12f, C_TEXT_2))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(chip)
        })
        addView(action)
    }

    internal fun chip(): TextView = text("", 12f, Color.WHITE, true).apply {
        setPadding(dp(10), dp(4), dp(10), dp(4))
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(C_GREY) }
    }

    /** level：1＝好了（綠）、0＝缺（紅）、3＝不適用（灰）、其他＝建議（橘） */
    internal fun setChip(c: TextView, level: Int, t: String) {
        c.text = t
        (c.background as GradientDrawable).setColor(when (level) { 1 -> C_GREEN; 0 -> C_RED; 3 -> C_GREY; else -> C_ORANGE })
    }

    private fun segPaint(b: Button, on: Boolean, color: Int) {
        (b.background as GradientDrawable).apply { setColor(if (on) color else Color.WHITE); setStroke(dp(2), color) }
        b.setTextColor(if (on) Color.WHITE else color)
    }

    internal fun btn(label: String, color: Int, weight: Float = 0f, textColor: Int = Color.WHITE, onClick: () -> Unit): Button = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f; setTypeface(typeface, Typeface.BOLD)
        // 白底按鈕（狀態卡上）：字用 textColor 傳進來的顏色
        val fill = color; val fg = if (color == Color.WHITE) textColor else Color.WHITE
        setTextColor(fg)
        background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(fill) }
        stateListAnimator = null
        setPadding(dp(14), dp(10), dp(14), dp(10))
        layoutParams = LinearLayout.LayoutParams(if (weight > 0f) 0 else LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, weight).apply {
            setMargins(dp(4), dp(6), dp(4), dp(6))
        }
        setOnClickListener { onClick(); refreshSoon() }
    }

    internal fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        val C_PURPLE = Color.rgb(123, 31, 162)
        val C_PINK = Color.rgb(236, 64, 122)
        val C_INDIGO = Color.rgb(57, 73, 171)
        val C_BLUE = Color.rgb(30, 136, 229)
        val C_TEAL = Color.rgb(0, 137, 123)
        val C_GREEN = Color.rgb(67, 160, 71)
        val C_RED = Color.rgb(229, 57, 53)
        val C_ORANGE = Color.rgb(245, 124, 0)
        val C_GREY = Color.rgb(120, 144, 156)
        val C_TEXT = Color.rgb(38, 50, 56)
        val C_TEXT_2 = Color.rgb(96, 125, 139)
        val C_BG_TOP = Color.rgb(237, 231, 246)
        val C_BG_BOTTOM = Color.rgb(224, 247, 250)
    }
}
