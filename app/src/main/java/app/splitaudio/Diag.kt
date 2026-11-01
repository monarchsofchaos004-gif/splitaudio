package app.splitaudio

import android.content.Context
import android.media.AudioManager
import android.os.Build
import rikka.shizuku.Shizuku

/** Collects the diagnostics for [Report] (needs a Context). Never reads account, contacts or Bluetooth device names. */
object Diag {
    fun versionName(c: Context): String = try { c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "?" } catch (_: Throwable) { "?" }

    fun mode(c: Context): String = if (Outputs.customFlag(c)) "custom" else "simple"

    private fun shizuku(): String {
        val alive = ShizukuBackend.alive()
        val ver = try { if (alive) Shizuku.getVersion().toString() else "-" } catch (_: Throwable) { "?" }
        return "running=$alive, version=$ver, permission=" + (if (ShizukuBackend.granted()) "granted" else "not granted")
    }

    private fun audioTypes(c: Context): List<String> = try {
        c.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { Report.deviceTypeName(it.type) }.sorted()
    } catch (_: Throwable) { emptyList() }

    fun info(c: Context) = Report.DiagInfo(
        Build.BRAND, Build.MODEL, Build.DISPLAY, Build.VERSION.RELEASE, Build.VERSION.SDK_INT,
        versionName(c), Dist.flavor, mode(c), shizuku(), audioTypes(c), AppLog.tail())

    fun text(c: Context): String = Report.diagnostics(info(c))

    private fun device() = Report.deviceName(Build.BRAND, Build.MODEL)
    private fun rom() = Report.romText(Build.DISPLAY, Build.VERSION.RELEASE, Build.VERSION.SDK_INT)
    private fun version(c: Context) = versionName(c) + " (" + Dist.flavor + ")"

    fun deviceLabel(): String = device()
    fun worksUrl(c: Context): String = Report.worksUrl(device(), rom(), version(c), mode(c), "Reported from the app's \"It works!\" button.")
    /** Problem report; notes = diagnostics (called from background threads too: collecting is read-only) */
    fun problemUrl(c: Context): String = Report.problemUrl(device(), rom(), version(c), mode(c), "What went wrong:\n\n\nDiagnostics:\n" + text(c))
}
