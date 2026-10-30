package app.splitaudio.adb

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 通知列 RemoteInput 送來的配對碼 */
class PairReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != ACT_CODE) return
        val code = RemoteInput.getResultsFromIntent(i)?.getCharSequence(Pairing.KEY_CODE)?.toString()?.trim().orEmpty()
        if (!SetupLogic.validCode(code)) {
            Pairing.failReason = "bad_code"; Pairing.set(Pairing.St.FOUND); Pairing.notifyState(c); return
        }
        Pairing.submit(c, code)
    }
    companion object { const val ACT_CODE = "app.splitaudio.adb.PAIR_CODE" }
}
