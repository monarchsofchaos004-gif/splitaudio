package app.splitaudio.adb

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import app.splitaudio.Edge
import app.splitaudio.R

/** 開源授權頁：顯示 assets/licenses.txt（元件清單＋Apache-2.0 全文） */
class LicensesActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        title = getString(R.string.lic_title)
        val text = try { assets.open("licenses.txt").bufferedReader().use { it.readText() } } catch (_: Exception) { "" }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val tv = TextView(this).apply { setPadding(pad, pad, pad, pad); textSize = 12f; setTextColor(Color.rgb(38, 50, 56)); this.text = text; setTextIsSelectable(true) }
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.WHITE); addView(tv) }
        setContentView(scroll)
        Edge.apply(scroll, tv)   // edge-to-edge：文字避開系統列
    }
}
