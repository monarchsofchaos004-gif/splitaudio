package app.splitaudio

import android.media.AudioDeviceInfo
import java.net.URLEncoder

/** Everything about "report it in one tap": the diagnostics text and the pre-filled GitHub issue URL.
 *  Pure functions only (unit-tested in ReportTest); the Context-dependent collecting lives in [Diag]. */
object Report {
    const val ISSUE_BASE = "https://github.com/monarchsofchaos004-gif/splitaudio/issues/new?template=device_report.yml"
    /** The only values the issue form's dropdowns accept (.github/ISSUE_TEMPLATE/device_report.yml) */
    const val WORKS = "works"
    const val PARTLY = "partly works"
    const val BROKEN = "does not work"
    const val UNTESTED = "not tested"
    private val DROPDOWN = setOf(WORKS, PARTLY, BROKEN, UNTESTED)
    const val MAX_URL = 8000
    const val LOG_LINES = 20

    /** What the diagnostics contain. No account, contact or Bluetooth device name is ever collected. */
    class DiagInfo(
        val brand: String, val model: String, val display: String,
        val androidRelease: String, val sdk: Int,
        val appVersion: String, val flavor: String, val mode: String,
        val shizuku: String,
        val audioTypes: List<String>,
        val log: List<String>,
    )

    fun deviceName(brand: String, model: String): String {
        val b = brand.trim(); val m = model.trim()
        return if (m.startsWith(b, ignoreCase = true) || b.isEmpty()) m else "$b $m".trim()
    }

    fun romText(display: String, androidRelease: String, sdk: Int) = "$display, Android $androidRelease (API $sdk)"

    /** Pure: audio output device TYPES only (never names). Unknown types are reported by number. */
    fun deviceTypeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "builtin_speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "builtin_earpiece"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bluetooth_a2dp"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bluetooth_sco"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "ble_headset"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "ble_speaker"
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> "ble_broadcast"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired_headphones"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired_headset"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "usb_device"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "usb_headset"
        AudioDeviceInfo.TYPE_HDMI -> "hdmi"
        AudioDeviceInfo.TYPE_HEARING_AID -> "hearing_aid"
        AudioDeviceInfo.TYPE_FM -> "fm"
        AudioDeviceInfo.TYPE_TELEPHONY -> "telephony"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "line_analog"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "line_digital"
        AudioDeviceInfo.TYPE_AUX_LINE -> "aux_line"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "usb_accessory"
        AudioDeviceInfo.TYPE_DOCK -> "dock"
        AudioDeviceInfo.TYPE_DOCK_ANALOG -> "dock_analog"
        AudioDeviceInfo.TYPE_HDMI_ARC -> "hdmi_arc"
        AudioDeviceInfo.TYPE_HDMI_EARC -> "hdmi_earc"
        AudioDeviceInfo.TYPE_IP -> "ip"
        AudioDeviceInfo.TYPE_BUS -> "bus"
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "remote_submix"
        else -> "type_$type"
    }

    /** Pure: the diagnostics block (English on purpose: it is for the developer). Keeps only the last [LOG_LINES] log lines. */
    fun diagnostics(i: DiagInfo): String = buildString {
        append("Device: ").append(deviceName(i.brand, i.model)).append(" (brand=").append(i.brand).append(", model=").append(i.model).append(")\n")
        append("Build.DISPLAY: ").append(i.display).append('\n')
        append("Android: ").append(i.androidRelease).append(" (API ").append(i.sdk).append(")\n")
        append("SplitAudio: ").append(i.appVersion).append(" (").append(i.flavor).append(")\n")
        append("Mode: ").append(i.mode).append('\n')
        append("Shizuku: ").append(i.shizuku).append('\n')
        append("Audio output device types: ").append(if (i.audioTypes.isEmpty()) "none" else i.audioTypes.joinToString(", ")).append('\n')
        append("Recent log:\n")
        val tail = i.log.takeLast(LOG_LINES)
        if (tail.isEmpty()) append("  (empty)\n") else tail.forEach { append("  ").append(it.take(300)).append('\n') }
    }

    /** Pure: [v] if it is one of the form's dropdown values, otherwise "not tested". */
    fun dropdown(v: String): String = if (v in DROPDOWN) v else UNTESTED

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** Pure: the pre-filled issue URL. Field ids = the ids in device_report.yml. Total length is at most [MAX_URL]:
     *  if it would be longer, [notes] is cut (never in the middle of a surrogate pair) and marked as truncated. */
    fun issueUrl(title: String, device: String, rom: String, version: String,
                 simple: String, custom: String, volkeys: String, notes: String): String {
        fun build(n: String) = ISSUE_BASE +
            "&title=" + enc(title) + "&device=" + enc(device) + "&rom=" + enc(rom) + "&version=" + enc(version) +
            "&simple=" + enc(dropdown(simple)) + "&custom=" + enc(dropdown(custom)) + "&volkeys=" + enc(dropdown(volkeys)) +
            "&notes=" + enc(n)
        var url = build(notes)
        if (url.length <= MAX_URL) return url
        val mark = "\n...(truncated, use Copy diagnostics for the full text)"
        var keep = notes.length
        while (keep > 0) {
            // shrink proportionally first, then fine-tune
            val over = url.length - MAX_URL
            keep = maxOf(0, keep - maxOf(1, over / 9))
            if (keep > 0 && Character.isHighSurrogate(notes[keep - 1])) keep--
            url = build(notes.substring(0, keep) + mark)
            if (url.length <= MAX_URL) return url
        }
        return build("").take(MAX_URL)
    }

    /** Pure: which dropdowns to pre-fill. mode = "simple"/"custom" (the mode the user is in). */
    fun fields(mode: String, verdict: String): Triple<String, String, String> =
        if (mode == "custom") Triple(UNTESTED, verdict, UNTESTED) else Triple(verdict, UNTESTED, UNTESTED)

    fun worksUrl(device: String, rom: String, version: String, mode: String, notes: String): String {
        val (s, c, v) = fields(mode, WORKS)
        return issueUrl("[device] $device: works", device, rom, version, s, c, v, notes)
    }

    fun problemUrl(device: String, rom: String, version: String, mode: String, notes: String): String {
        val (s, c, v) = fields(mode, BROKEN)
        return issueUrl("[device] $device: not working", device, rom, version, s, c, v, notes)
    }
}
