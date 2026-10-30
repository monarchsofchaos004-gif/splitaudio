package app.splitaudio

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView

/** 每個 App 分開調音量／靜音（0.4）。正在出聲的 App 排最前面，再來是調過的，再照名稱。
 *  放開拉桿或按靜音就馬上套用（Mixer.apply，背景執行緒）。只列有桌面圖示的 App（同 AppsActivity）。 */
class MixerActivity : Activity() {
    private class App(val pkg: String, val uid: Int, val label: String, val icon: Drawable)
    private var all = listOf<App>()
    private var shown = listOf<App>()
    private var playing = setOf<Int>()
    private var query = ""
    private lateinit var adapter: BaseAdapter
    private lateinit var status: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val dp = resources.displayMetrics.density
        val pad = (12 * dp).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, 0)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(MainActivity.C_BG_TOP, MainActivity.C_BG_BOTTOM))
        }
        root.addView(TextView(this).apply { textSize = 22f; text = "🔉 " + getString(R.string.mixer_title); setTextColor(MainActivity.C_PURPLE); setTypeface(typeface, Typeface.BOLD) })
        root.addView(TextView(this).apply { textSize = 13f; text = getString(R.string.mixer_hint); setTextColor(MainActivity.C_TEXT_2); setPadding(0, pad / 2, 0, pad / 4) })
        status = TextView(this).apply { textSize = 12f; setTextColor(MainActivity.C_TEXT_2); setPadding(0, 0, 0, pad / 2) }
        root.addView(status)
        root.addView(EditText(this).apply {
            hint = "🔍 " + getString(R.string.apps_search); isSingleLine = true; textSize = 15f
            setPadding(pad, pad * 3 / 4, pad, pad * 3 / 4)
            background = GradientDrawable().apply { cornerRadius = 24 * dp; setColor(Color.WHITE); setStroke((2 * dp).toInt(), Color.rgb(206, 147, 216)) }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { query = s?.toString().orEmpty().trim(); filter() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        })
        adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(i: Int): Any = shown[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, v: View?, parent: ViewGroup?): View {
                val row = (v as? LinearLayout) ?: row(dp)
                bind(row, shown[i])
                return row
            }
        }
        val lv = ListView(this).apply {
            adapter = this@MixerActivity.adapter; divider = null; dividerHeight = (8 * dp).toInt(); setPadding(0, pad, 0, pad); clipToPadding = false
        }
        root.addView(lv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        Edge.apply(root, root, lv)   // edge-to-edge：上／左右避開系統列，清單的底部留出導覽列／鍵盤的位置
        Thread {
            val pm = packageManager
            val l = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }.filter { it.packageName != packageName }
                .map { App(it.packageName, it.uid, pm.getApplicationLabel(it).toString(), pm.getApplicationIcon(it)) }
            runOnUiThread { all = l; filter() }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        val c = applicationContext
        Thread {
            if (Backends.current() == null) { runOnUiThread { status.text = getString(R.string.mixer_need_setup) }; return@Thread }
            Route.deploy(c)
            Mixer.ensure(c)
            val p = Mixer.playingUids(c).map { it % 100000 }.toSet()   // 第二空間／工作設定檔的 uid 也算同一個 App
            runOnUiThread { playing = p; status.text = getString(R.string.mixer_playing_n, all.count { it.uid % 100000 in p }); filter() }
        }.start()
    }

    private fun row(dp: Float): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        val p = (10 * dp).toInt(); setPadding(p + p / 2, p, p, p)
        background = GradientDrawable().apply { cornerRadius = 18 * dp; setColor(Color.WHITE) }
        addView(ImageView(this@MixerActivity), LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).apply { rightMargin = (12 * dp).toInt() })
        addView(LinearLayout(this@MixerActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MixerActivity).apply { textSize = 15f; setTextColor(MainActivity.C_TEXT); setTypeface(typeface, Typeface.BOLD) })
            addView(SeekBar(this@MixerActivity).apply { max = 100 })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(this@MixerActivity).apply {   // 靜音鈕
            textSize = 22f; gravity = Gravity.CENTER; val h = (8 * dp).toInt(); setPadding(h, h, h, h)
        })
    }

    private fun bind(row: LinearLayout, a: App) {
        val v = Mixer.get(this, a.pkg)
        (row.getChildAt(0) as ImageView).setImageDrawable(a.icon)
        val col = row.getChildAt(1) as LinearLayout
        val title = col.getChildAt(0) as TextView
        val bar = col.getChildAt(1) as SeekBar
        val mute = row.getChildAt(2) as TextView
        fun label(x: Int) = (if (a.uid % 100000 in playing) "▶ " else "") + a.label + "　" +
            (if (x == 0) getString(R.string.mixer_muted) else "$x%")
        title.text = label(v)
        bar.setOnSeekBarChangeListener(null)
        bar.progress = v
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) { if (user) title.text = label(p) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { save(a, s?.progress ?: 100) }
        })
        mute.text = if (v == 0) "🔇" else "🔊"
        mute.setOnClickListener { save(a, if (Mixer.get(this, a.pkg) == 0) Mixer.last(this, a.pkg) else 0) }
    }

    private fun save(a: App, v: Int) {
        Mixer.set(this, a.pkg, v)
        adapter.notifyDataSetChanged()
        val c = applicationContext
        Thread { val r = Mixer.apply(c); runOnUiThread { status.text = r } }.start()
    }

    /** 正在出聲的排前面，再來是調過的，再照名稱；搜尋比對名稱或套件名 */
    private fun filter() {
        shown = all.filter { query.isEmpty() || it.label.contains(query, true) || it.pkg.contains(query, true) }
            .sortedWith(compareBy<App>({ it.uid % 100000 !in playing }, { Mixer.get(this, it.pkg) == 100 }, { it.label.lowercase() }))
        adapter.notifyDataSetChanged()
    }
}
