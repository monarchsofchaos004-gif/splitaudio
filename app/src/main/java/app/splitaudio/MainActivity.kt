package app.splitaudio

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** 狀態＋設定頁（字串在 res/values*，英文預設、繁中 values-zh-rTW）。
 *  上：狀態卡（分流中／等音樂／沒在跑，顏色跟著變）＋啟動／停止；① 必要設定：Shizuku、音量鍵（無障礙）；
 *  ② 客製化：簡單／自訂模式、每個 App 選輸出、開影片不打斷音樂。 */
class MainActivity : Activity() {
    private lateinit var liveCard: LinearLayout
    private lateinit var tvLive: TextView
    private lateinit var tvLiveSub: TextView
    private lateinit var tvLast: TextView
    private lateinit var shzChip: TextView
    private lateinit var shzBtn: Button
    private lateinit var accChip: TextView
    private lateinit var accBtn: Button
    private lateinit var segSimple: Button
    private lateinit var segCustom: Button
    private lateinit var appsBtn: Button
    private lateinit var mfSw: Switch
    private lateinit var tvMf: TextView
    private lateinit var tvModeDesc: TextView
    @Volatile private var mf: String? = null
    private var mfBusy = false

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

        // 狀態卡（顏色跟著狀態）＋手動啟動／停止
        liveCard = card(C_GREY)
        tvLive = text("…", 20f, Color.WHITE, true)
        tvLiveSub = text("", 13f, Color.WHITE).apply { setPadding(0, dp(4), 0, dp(6)) }
        liveCard.addView(tvLive); liveCard.addView(tvLiveSub)
        liveCard.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(btn("▶ " + getString(R.string.btn_start), Color.WHITE, 1f, C_GREEN) { Thread { Route.start(this@MainActivity) }.start() })
            addView(btn("■ " + getString(R.string.btn_stop), Color.WHITE, 1f, C_RED) { Thread { Route.stop() }.start() })
        })
        tvLast = text("", 11f, Color.argb(220, 255, 255, 255)).apply { setPadding(dp(4), dp(2), 0, 0) }
        liveCard.addView(tvLast)
        root.addView(liveCard)

        // ① 必要設定
        val req = card(Color.WHITE)
        req.addView(section(getString(R.string.sec_required), C_TEAL))
        shzChip = chip(); shzBtn = btn(getString(R.string.btn_grant), C_ORANGE) { Shz.request() }
        req.addView(row("🔑", getString(R.string.row_shizuku), getString(R.string.row_shizuku_d), shzChip, shzBtn))
        req.addView(divider())
        accChip = chip(); accBtn = btn(getString(R.string.btn_open), C_BLUE) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        req.addView(row("🔊", getString(R.string.row_acc), getString(R.string.row_acc_d), accChip, accBtn))
        root.addView(req)

        // ② 客製化
        val cus = card(Color.WHITE)
        cus.addView(section(getString(R.string.sec_custom), C_PURPLE))
        cus.addView(text(getString(R.string.mode_title), 15f, C_TEXT, true))
        segSimple = btn(getString(R.string.mode_simple), C_TEAL, 1f) { setMode(false) }
        segCustom = btn(getString(R.string.mode_custom), C_PURPLE, 1f) { setMode(true) }
        cus.addView(LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(segSimple); addView(segCustom) })
        tvModeDesc = text("", 13f, C_TEXT_2).apply { setPadding(dp(4), 0, dp(4), dp(4)) }
        cus.addView(tvModeDesc)
        appsBtn = btn("🎛️ " + getString(R.string.btn_apps), C_INDIGO) { startActivity(Intent(this, AppsActivity::class.java)) }
        cus.addView(appsBtn)
        cus.addView(btn("🔉 " + getString(R.string.btn_mixer), C_PINK) { startActivity(Intent(this, MixerActivity::class.java)) })
        cus.addView(divider())
        mfSw = Switch(this).apply {
            text = "🎬 " + getString(R.string.mf_title); textSize = 15f; setTextColor(C_TEXT); setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(2))
            setOnCheckedChangeListener { _, on ->
                if (mfBusy || on == (mf == "on")) return@setOnCheckedChangeListener
                Thread { Route.lastStart = getString(R.string.mf_result, Route.setMultiFocus(this@MainActivity, on)); refreshSoon() }.start()
            }
        }
        cus.addView(mfSw)
        tvMf = text("", 13f, C_TEXT_2).apply { setPadding(0, 0, 0, dp(4)) }
        cus.addView(tvMf)
        root.addView(cus)

        setContentView(ScrollView(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(C_BG_TOP, C_BG_BOTTOM))
            addView(root)
        })
    }

    override fun onResume() { super.onResume(); refreshSoon() }

    private fun setMode(custom: Boolean) {
        if (custom == Outputs.customFlag(this)) return
        Outputs.setCustom(this, custom); val c = applicationContext
        Thread { Route.apply(c) }.start()
        if (custom && !Outputs.hasAny(this)) startActivity(Intent(this, AppsActivity::class.java))
        refreshSoon()
    }

    private fun refreshSoon() {
        Thread {
            Thread.sleep(600)
            val acc = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").contains("$packageName/")
            val s = Route.ask("state")
            val alive = Shz.alive(); val granted = Shz.granted()
            mf = if (granted) Route.multiFocusState() else null
            val custom = Outputs.customFlag(this); val nApps = Outputs.all(this).size
            runOnUiThread {
                val (live, color) = when (s) {
                    "on" -> Pair("🟢 " + getString(R.string.live_on), C_GREEN)
                    "off" -> Pair("🔵 " + getString(R.string.live_wait), C_BLUE)
                    else -> Pair("⚪ " + getString(R.string.live_none), C_GREY)
                }
                tvLive.text = live
                (liveCard.background as GradientDrawable).setColor(color)
                tvLiveSub.text = if (custom && nApps > 0) getString(R.string.st_mode_custom, nApps) else getString(R.string.st_mode_simple)
                tvLast.text = getString(R.string.st_last, Route.lastStart)

                setChip(shzChip, if (granted) 1 else 0, getString(if (!alive) R.string.v_not_running else if (granted) R.string.v_granted else R.string.v_not_granted))
                shzBtn.visibility = if (granted) android.view.View.GONE else android.view.View.VISIBLE
                setChip(accChip, if (acc) 1 else 2, getString(if (acc) R.string.v_on else R.string.chip_recommended))
                accBtn.visibility = if (acc) android.view.View.GONE else android.view.View.VISIBLE

                segPaint(segSimple, !custom, C_TEAL); segPaint(segCustom, custom, C_PURPLE)
                tvModeDesc.text = getString(if (custom && nApps == 0) R.string.mode_custom_empty else if (custom) R.string.mode_custom_d else R.string.mode_simple_d)
                appsBtn.visibility = if (custom) android.view.View.VISIBLE else android.view.View.GONE

                mfBusy = true; mfSw.isChecked = mf == "on"; mfSw.isEnabled = granted; mfBusy = false
                tvMf.text = when (mf) { "on" -> getString(R.string.mf_desc); "off" -> getString(R.string.mf_off_auto); "off_user" -> getString(R.string.mf_off_user); else -> getString(R.string.mf_desc) }
            }
        }.start()
    }

    // ---- 小元件 ----
    private fun card(bg: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(bg) }
        elevation = dp(3).toFloat()
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
    }

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun section(t: String, color: Int): TextView = text(t, 19f, color, true).apply { setPadding(0, 0, 0, dp(10)) }

    private fun divider() = android.view.View(this).apply {
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

    private fun chip(): TextView = text("", 12f, Color.WHITE, true).apply {
        setPadding(dp(10), dp(4), dp(10), dp(4))
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(C_GREY) }
    }

    /** level：1＝好了（綠）、0＝缺（紅）、2＝建議（橘） */
    private fun setChip(c: TextView, level: Int, t: String) {
        c.text = t
        (c.background as GradientDrawable).setColor(when (level) { 1 -> C_GREEN; 0 -> C_RED; else -> C_ORANGE })
    }

    private fun segPaint(b: Button, on: Boolean, color: Int) {
        (b.background as GradientDrawable).apply { setColor(if (on) color else Color.WHITE); setStroke(dp(2), color) }
        b.setTextColor(if (on) Color.WHITE else color)
    }

    private fun btn(label: String, color: Int, weight: Float = 0f, textColor: Int = Color.WHITE, onClick: () -> Unit): Button = Button(this).apply {
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

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
