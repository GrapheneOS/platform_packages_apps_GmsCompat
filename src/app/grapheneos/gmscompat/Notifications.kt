package app.grapheneos.gmscompat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.content.Intent
import java.util.concurrent.atomic.AtomicInteger

object Notifications {
    const val CH_PERSISTENT_FG_SERVICE = "persistent_fg_service"
    const val CH_PLAY_STORE_PENDING_USER_ACTION = "play_store_pending_user_action"
    const val CH_MISSING_PERMISSION = "missing_permission"
    const val CH_MISSING_PLAY_GAMES_APP = "missing_play_games_app"
    const val CH_BACKGROUND_ACTIVITY_START = "bg_activity_start"
    const val CH_GMS_CRASHED = "gms_crashed"

    const val ID_PERSISTENT_FG_SERVICE = 1
    const val ID_PLAY_STORE_PENDING_USER_ACTION = 2
    const val ID_PLAY_STORE_MISSING_OBB_PERMISSION = 3
    const val ID_GMS_CORE_MISSING_NEARBY_DEVICES_PERMISSION = 4
    const val ID_MISSING_NEARBY_DEVICES_PERMISSION_GENERIC = 5
    const val ID_MISSING_PLAY_GAMES_APP = 6

    private val uniqueNotificationId = AtomicInteger(10_000)
    fun generateUniqueNotificationId() = uniqueNotificationId.getAndIncrement()

    @JvmStatic
    fun createNotificationChannels() {
        val list = listOf(
            ch(CH_PERSISTENT_FG_SERVICE, R.string.persistent_fg_service_notif),
            ch(CH_PLAY_STORE_PENDING_USER_ACTION, R.string.play_store_pending_user_action_notif),
            ch(CH_MISSING_PERMISSION, R.string.missing_permission, IMPORTANCE_HIGH),
            ch(CH_MISSING_PLAY_GAMES_APP, R.string.missing_play_games_app, IMPORTANCE_HIGH),
            ch(CH_BACKGROUND_ACTIVITY_START, R.string.notif_channel_bg_activity_start, IMPORTANCE_HIGH),
            ch(CH_GMS_CRASHED, R.string.notif_gms_crash_title, IMPORTANCE_HIGH),
        )

        App.notificationManager().createNotificationChannels(list)
    }

    private fun ch(id: String, title: Int, importance: Int = NotificationManager.IMPORTANCE_LOW)
        = NotificationChannel(id, App.ctx().getText(title), importance)

    fun configurationRequired(channel: String,
            title: CharSequence, text: CharSequence,
            resolutionText: CharSequence, resolutionIntent: Intent): Notification.Builder
    {
        val ctx = App.ctx()
        val pendingIntent = PendingIntent.getActivity(ctx, 0, freshActivity(resolutionIntent), PendingIntent.FLAG_IMMUTABLE)

        val resolution = Notification.Action.Builder(null, resolutionText, pendingIntent).build()

        return builder(channel)
            .setSmallIcon(R.drawable.ic_configuration_required)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setOnlyAlertOnce(true)
            .addAction(resolution)
    }

    @JvmStatic
    fun builder(channel: String) = Notification.Builder(App.ctx(), channel)

    fun cancel(id: Int) {
        App.notificationManager().cancel(id)
    }
}

fun Notification.Builder.show(id: Int) {
    App.notificationManager().notify(id, this.build())
}
