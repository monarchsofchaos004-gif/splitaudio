package app.splitaudio

/** "Report a problem": what went wrong, picked with one tap. The report always carries the English line, whatever the app
 *  language, so every report reads the same on GitHub; the free-text note follows it. */
object Symptoms {
    /** key -> English text sent in the report; the on-screen labels are the string resources sym_<key>. */
    val ALL = listOf(
        "bt_others" to "Other apps still play on Bluetooth",
        "speaker_music" to "Music plays on the phone speaker",
        "no_sound" to "No sound at all",
        "calls" to "Phone calls are affected",
        "volkeys" to "Volume keys change the wrong sound",
        "pauses" to "Music pauses or stops by itself",
        "other" to "Something else",
    )

    /** key -> on-screen label (follows the app language) */
    val LABELS = mapOf("bt_others" to R.string.sym_bt_others, "speaker_music" to R.string.sym_speaker_music,
        "no_sound" to R.string.sym_no_sound, "calls" to R.string.sym_calls, "volkeys" to R.string.sym_volkeys,
        "pauses" to R.string.sym_pauses, "other" to R.string.sym_other)

    /** The note sent to the server: "Symptoms: a; b" first (when any was picked), then what the user typed. */
    fun note(picked: Collection<String>, free: String): String {
        val line = ALL.filter { it.first in picked }.joinToString("; ") { it.second }
        val f = free.trim()
        return when {
            line.isEmpty() -> f
            f.isEmpty() -> "Symptoms: $line"
            else -> "Symptoms: $line\n$f"
        }
    }
}
