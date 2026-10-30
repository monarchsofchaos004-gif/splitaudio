package app.splitaudio

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** 自訂模式：每個 App 選輸出。只列有桌面圖示的 App（manifest <queries> MAIN/LAUNCHER，不用 QUERY_ALL_PACKAGES）。
 *  改過才在離開頁面時套用（停掉路由，監看服務照新設定重開）。 */
class AppsActivity : Activity() {
    private class App(val pkg: String, val label: String, val icon: Drawable)
    private var all = listOf<App>()
    private var shown = listOf<App>()
    private var query = ""
    private var changed = false
    private lateinit var adapter: BaseAdapter

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val dp = resources.displayMetrics.density
        val pad = (12 * dp).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, 0)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(MainActivity.C_BG_TOP, MainActivity.C_BG_BOTTOM))
        }
        root.addView(TextView(this).apply { textSize = 22f; text = "🎛️ " + getString(R.string.apps_title); setTextColor(MainActivity.C_PURPLE); setTypeface(typeface, Typeface.BOLD) })
        root.addView(TextView(this).apply { textSize = 13f; text = getString(R.string.apps_hint); setTextColor(MainActivity.C_TEXT_2); setPadding(0, pad / 2, 0, pad / 2) })
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
                val a = shown[i]; val o = Outputs.get(this@AppsActivity, a.pkg)
                (row.getChildAt(0) as ImageView).setImageDrawable(a.icon)
                val col = row.getChildAt(1) as LinearLayout
                (col.getChildAt(0) as TextView).text = a.label
                (col.getChildAt(1) as TextView).apply {   // 輸出膠囊：喇叭橘、藍牙藍、耳機綠、預設灰
                    text = when (o) { "speaker" -> "📱 "; "bt" -> "🎧 "; "wired" -> "🔌 "; else -> "" } + Outputs.label(this@AppsActivity, o)
                    (background as GradientDrawable).setColor(when (o) { "speaker" -> MainActivity.C_ORANGE; "bt" -> MainActivity.C_BLUE; "wired" -> MainActivity.C_GREEN; else -> Color.rgb(207, 216, 220) })
                    setTextColor(if (o == "default") MainActivity.C_TEXT_2 else Color.WHITE)
                }
                return row
            }
        }
        val lv = ListView(this).apply {
            adapter = this@AppsActivity.adapter; divider = null; dividerHeight = (8 * dp).toInt(); setPadding(0, pad, 0, pad); clipToPadding = false
            setOnItemClickListener { _, _, i, _ -> pick(shown[i]) }
        }
        root.addView(lv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        Edge.apply(root, root, lv)   // edge-to-edge：上／左右避開系統列，清單的底部留出導覽列／鍵盤的位置
        Thread {
            val pm = packageManager
            val l = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }.filter { it.packageName != packageName }
                .map { App(it.packageName, pm.getApplicationLabel(it).toString(), pm.getApplicationIcon(it)) }
            runOnUiThread { all = l; filter() }
        }.start()
    }

    private fun row(dp: Float): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
        val p = (10 * dp).toInt(); setPadding(p + p / 2, p, p, p)
        background = GradientDrawable().apply { cornerRadius = 18 * dp; setColor(Color.WHITE) }
        addView(ImageView(this@AppsActivity), LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).apply { rightMargin = (12 * dp).toInt() })
        addView(LinearLayout(this@AppsActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@AppsActivity).apply { textSize = 16f; setTextColor(MainActivity.C_TEXT); setTypeface(typeface, Typeface.BOLD) })
            addView(TextView(this@AppsActivity).apply {
                textSize = 12f; setTypeface(typeface, Typeface.BOLD)
                val h = (10 * dp).toInt(); val v = (3 * dp).toInt(); setPadding(h, v, h, v)
                background = GradientDrawable().apply { cornerRadius = 12 * dp; setColor(Color.LTGRAY) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (4 * dp).toInt() })
        })
    }

    /** 有指定輸出的排前面，再照名稱；搜尋比對名稱或套件名 */
    private fun filter() {
        shown = all.filter { query.isEmpty() || it.label.contains(query, true) || it.pkg.contains(query, true) }
            .sortedWith(compareBy<App>({ Outputs.get(this, it.pkg) == "default" }, { it.label.lowercase() }))
        adapter.notifyDataSetChanged()
    }

    private fun pick(a: App) {
        val cur = Outputs.KEYS.indexOf(Outputs.get(this, a.pkg)).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle(a.label)
            .setSingleChoiceItems(Outputs.KEYS.map<String, CharSequence> { Outputs.label(this, it) }.toTypedArray(), cur) { d, w ->
                Outputs.set(this, a.pkg, Outputs.KEYS[w]); changed = true; adapter.notifyDataSetChanged(); d.dismiss()
            }.show()
    }

    override fun onPause() {
        super.onPause()
        if (changed) { changed = false; val c = applicationContext; Thread { Route.apply(c) }.start() }
    }
}
