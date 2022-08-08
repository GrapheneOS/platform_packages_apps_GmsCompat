package app.grapheneos.gmscompat

import android.app.ApplicationErrorReport
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.util.*
import java.util.concurrent.TimeUnit

class CrashReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val stackTrace = intent?.getParcelableExtra<ApplicationErrorReport>(Intent.EXTRA_BUG_REPORT)
                ?.crashInfo?.stackTrace

        if (stackTrace == null) {
            return
        }

        val ts = SystemClock.elapsedRealtime()

        synchronized(javaClass) {
            if (stackTrace == prevNotifStackTrace) {
                val prev = prevNotifTimestamp
                if (prev != 0L && (ts - prev) < TimeUnit.MINUTES.toMillis(5)) {
                    // don't spam notifications if GMS chain-crashes
                    return
                }
            }
            prevNotifStackTrace = stackTrace
            prevNotifTimestamp = ts
        }

        val ctx = App.ctx()

        intent.setComponent(ComponentName.createRelative("com.android.systemui", ".ErrorReportActivity"))
        intent.setIdentifier(UUID.randomUUID().toString())

        val pendingIntent = PendingIntent.getActivity(ctx, 0, intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)

        Notifications.builder(Notifications.CH_GMS_CRASHED).apply {
            setContentTitle(ctx.getText(R.string.notif_gms_crash_title))
            setContentText(ctx.getText(R.string.notif_gms_crash_text))
            setContentIntent(pendingIntent)
            setShowWhen(true)
            setAutoCancel(true)
            setSmallIcon(R.drawable.ic_crash_report)
        }.show(Notifications.generateUniqueNotificationId())
    }

    companion object {
        var prevNotifStackTrace: String? = null
        var prevNotifTimestamp = 0L
    }
}
