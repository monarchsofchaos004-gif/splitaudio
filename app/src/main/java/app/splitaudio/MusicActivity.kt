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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** 簡單模式：選哪些是「音樂 App」（它們的聲音留在藍牙，其他 App 走手機喇叭）。可以多選，至少留一個。
 *  只列有桌面圖示的 App（manifest <queries> MAIN/LAUNCHER，不用 QUERY_ALL_PACKAGES）。已勾的排前面。
 *  改過才在離開頁面時套用（停掉路由，監看服務照新設定重開；新清單由 Route.start 寫給路由程式）。 */
class MusicActivity : Activity() {
    private class App(val pkg: String, val label: String, val icon: Drawable)
    private var all = listOf<App>()
    private var shown = listOf<App>()
    private var query = ""
    private var changed = false
    private var selected: Set<String> = emptySet()
    private lateinit var adapter: BaseAdapter

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        selected = Music.set(this)
        val dp = resources.displayMetrics.density
        val pad = (12 * dp).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, 0)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(MainActivity.C_BG_TOP, MainActivity.C_BG_BOTTOM))
        }
        root.addView(TextView(this).apply { textSize = 22f; text = "🎵 " + getString(R.string.music_title); setTextColor(MainActivity.C_PURPLE); setTypeface(typeface, Typeface.BOLD) })
        root.addView(TextView(this).apply { textSize = 13f; text = getString(R.string.music_hint); setTextColor(MainActivity.C_TEXT_2); setPadding(0, pad / 2, 0, pad / 2) })
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
                val a = shown[i]
                (row.getChildAt(0) as ImageView).setImageDrawable(a.icon)
                val col = row.getChildAt(1) as LinearLayout
                (col.getChildAt(0) as TextView).text = a.label
                (col.getChildAt(1) as TextView).text = a.pkg
                (row.getChildAt(2) as CheckBox).isChecked = a.pkg in selected
                return row
            }
        }
        val lv = ListView(this).apply {
            adapter = this@MusicActivity.adapter; divider = null; dividerHeight = (8 * dp).toInt(); setPadding(0, pad, 0, pad); clipToPadding = false
            setOnItemClickListener { _, _, i, _ -> toggle(shown[i]) }
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

    /** 一列：圖示＋名稱／套件名（兩個版本的 YT Music 名稱一樣，靠套件名分辨）＋勾選框（只顯示，點整列切換） */
    private fun row(dp: Float): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        val p = (10 * dp).toInt(); setPadding(p + p / 2, p, p, p)
        background = GradientDrawable().apply { cornerRadius = 18 * dp; setColor(Color.WHITE) }
        addView(ImageView(this@MusicActivity), LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).apply { rightMargin = (12 * dp).toInt() })
        addView(LinearLayout(this@MusicActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MusicActivity).apply { textSize = 16f; setTextColor(MainActivity.C_TEXT); setTypeface(typeface, Typeface.BOLD) })
            addView(TextView(this@MusicActivity).apply { textSize = 11f; setTextColor(MainActivity.C_TEXT_2) })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(CheckBox(this@MusicActivity).apply { isClickable = false; isFocusable = false; isFocusableInTouchMode = false })
    }

    /** 已勾的排前面，再照名稱；搜尋比對名稱或套件名。只在載入／搜尋時重排（勾選時不重排，免得列在手指下跳走）。 */
    private fun filter() {
        shown = all.filter { query.isEmpty() || it.label.contains(query, true) || it.pkg.contains(query, true) }
            .sortedWith(compareBy<App>({ it.pkg !in selected }, { it.label.lowercase() }))
        adapter.notifyDataSetChanged()
    }

    private fun toggle(a: App) {
        val next = Music.toggled(selected, a.pkg)
        if (next == selected) { Toast.makeText(this, R.string.music_last, Toast.LENGTH_SHORT).show(); return }   // 最後一個不能取消
        selected = next; Music.save(this, next); changed = true
        adapter.notifyDataSetChanged()
    }

    override fun onPause() {
        super.onPause()
        if (changed) { changed = false; val c = applicationContext; Thread { Route.apply(c) }.start() }
    }
}
