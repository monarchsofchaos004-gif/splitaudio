package app.splitaudio

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Edge-to-edge（targetSdk 35 起 Android 15+ 的畫面會延伸到狀態列／導覽列底下）：
 *  把系統列、瀏海、鍵盤的 insets 加到內容的 padding，內容才不會被蓋住。
 *  [outer] 掛監聽（Activity 的根 View）；[pad] 是實際加 padding 的 View（預設同 outer，例如捲動頁的內層）；
 *  [bottomPad] 底部的 insets 加在誰身上（預設同 pad；清單頁給 ListView，清單才能捲到導覽列底下又不被蓋住）。
 *  Android 14 以前的舊式視窗系統已經自己避開系統列，這裡拿到的 insets 是 0，不會重複加。 */
object Edge {
    fun apply(outer: View, pad: View = outer, bottomPad: View = pad) {
        val l = pad.paddingLeft; val t = pad.paddingTop; val r = pad.paddingRight
        val bl = bottomPad.paddingLeft; val bt = bottomPad.paddingTop; val br = bottomPad.paddingRight; val bb = bottomPad.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(outer) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val bottom = maxOf(bars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            if (bottomPad === pad) {
                pad.setPadding(l + bars.left, t + bars.top, r + bars.right, bb + bottom)
            } else {
                pad.setPadding(l + bars.left, t + bars.top, r + bars.right, pad.paddingBottom)
                bottomPad.setPadding(bl, bt, br, bb + bottom)
            }
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(outer)
    }
}
