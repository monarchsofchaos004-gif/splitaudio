package app.splitaudio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** 需要用戶處理的提醒：「alert」頻道（Play 版的配對提醒用同一個頻道）。點通知開主畫面。 */
object Alerts {
    const val CH_ALERT = "alert"
    /** 「開影片會不會暫停音樂？」的通知編號（1＝常駐、2＝配對、3＝無線偵錯提醒已被用） */
    const val ID_MF = 4
    /** "Something went wrong" (opens the pre-filled issue) */
    const val ID_FAIL = 5

    fun channel(c: Context) {
        c.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CH_ALERT, c.getString(R.string.ch_alert), NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** Notification whose tap opens [url] in the browser (the pre-filled issue form) */
    fun postLink(c: Context, id: Int, title: String, text: String, url: String) {
        try {
            channel(c)
            val pi = PendingIntent.getActivity(c, id, Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)), PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(c, CH_ALERT).setSmallIcon(R.drawable.ic_stat_route)
                .setContentTitle(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true).setContentIntent(pi).build()
            c.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (_: Exception) {}
    }

    fun post(c: Context, id: Int, title: String, text: String) {
        try {
            channel(c)
            val pi = PendingIntent.getActivity(c, id, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(c, CH_ALERT).setSmallIcon(R.drawable.ic_stat_route)
                .setContentTitle(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true).setContentIntent(pi).build()
            c.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (_: Exception) {}
    }
}
