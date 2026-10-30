package app.splitaudio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** 桌面（主畫面）App 的套件名，動態查（HOME intent）——不同廠牌的桌面套件名不一樣（小米 com.miui.home、AOSP com.android.launcher3、…）。
 *  音量鍵功能用它判斷「現在在桌面／系統介面」（那裡不攔音量鍵）。查一次存 60 秒。
 *  Android 11 起查別的 App 要在 manifest 的 <queries> 宣告（見 AndroidManifest 的 HOME intent）。 */
object Launcher {
    const val TTL_MS = 60_000L
    /** 一定算進去的系統介面 */
    val ALWAYS: Set<String> = setOf("com.android.systemui")
    @Volatile private var cache: Set<String> = ALWAYS
    @Volatile private var at = 0L

    /** 桌面 App ∪ 系統介面。查不到（例外）時沿用上次的結果。 */
    fun packages(c: Context, now: Long = System.currentTimeMillis()): Set<String> {
        if (fresh(at, now)) return cache
        val found = try {
            c.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY
            ).map { it.activityInfo?.packageName }
        } catch (_: Exception) { return cache }
        cache = merge(found); at = now
        return cache
    }

    /** 純邏輯（可單元測試）：查到的套件名 ∪ 系統介面（去掉 null／空字串） */
    fun merge(found: Collection<String?>): Set<String> = (found.filterNotNull().filter { it.isNotEmpty() } + ALWAYS).toSet()

    /** 純邏輯：快取還新鮮嗎（手機時間被往回調也當過期） */
    fun fresh(at: Long, now: Long, ttl: Long = TTL_MS): Boolean = at > 0L && now >= at && now - at < ttl
}
