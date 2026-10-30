package app.splitaudio.adb

/** 純邏輯：引導頁每一步的完成狀態、開機／換網路後該做什麼。有 JVM 單元測試。 */
object SetupLogic {
    data class Inputs(
        val devOptions: Boolean,     // 開發者選項已開
        val wifi: Boolean,           // 目前連著 WiFi
        val wirelessDebug: Boolean,  // 無線偵錯目前是開的
        val paired: Boolean,         // 配對過（本機記錄）
        val secureGranted: Boolean,  // 已有 WRITE_SECURE_SETTINGS
        val connected: Boolean,      // ADB 目前連著
    )

    /** 四步各自是否完成。第 2 步：配對後無線偵錯重開機會被系統關掉，不能因此倒退，所以 paired 也算完成。 */
    fun steps(i: Inputs): List<Boolean> {
        val s1 = i.devOptions || i.paired
        val s2 = i.wirelessDebug || i.paired
        val s3 = i.paired
        val s4 = i.paired && i.secureGranted
        return listOf(s1, s2, s3, s4)
    }

    /** 目前該做的步驟（0 起算）；全完成＝-1 */
    fun current(i: Inputs): Int = steps(i).indexOfFirst { !it }

    enum class Action { NONE, CONNECT, ENABLE_WD_THEN_CONNECT, NOTIFY_ENABLE_WD }

    /** 開機／WiFi 變成可用時要做什麼。routeRunning＝路由程式已在跑（不需要 ADB，別去打擾）。 */
    fun reconnect(i: Inputs, routeRunning: Boolean): Action = when {
        !i.paired || !i.wifi -> Action.NONE
        routeRunning || i.connected -> Action.NONE
        i.wirelessDebug -> Action.CONNECT
        i.secureGranted -> Action.ENABLE_WD_THEN_CONNECT
        else -> Action.NOTIFY_ENABLE_WD
    }

    /** 配對碼：只收 6 位數字 */
    fun validCode(s: String): Boolean = s.length == 6 && s.all { it in '0'..'9' }
}
