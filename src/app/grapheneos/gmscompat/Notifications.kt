package app.grapheneos.gmscompat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.concurrent.atomic.AtomicInteger

object Notifications {
    enum class Channel(val id: String, val title: Int, val importance: Int = NotificationManager.IMPORTANCE_LOW) {
        PERSISTENT_FG_SERVICE("persistent_fg_service",
                R.string.persistent_fg_service_notif),
        PLAY_STORE_PENDING_USER_ACTION("play_store_pending_user_action",
                R.string.play_store_pending_user_action_notif),
        MISSING_PERMISSION("missing_permission",
                R.string.missing_permission, NotificationManager.IMPORTANCE_HIGH),
        MISSING_PLAY_GAMES_APP("missing_play_games_app",
                R.string.missing_play_games_app, NotificationManager.IMPORTANCE_HIGH),
        BACKGROUND_ACTIVITY_START("bg_activity_start",
                R.string.notif_channel_bg_activity_start, NotificationManager.IMPORTANCE_HIGH),
        ;

        fun notifBuilder(): Notification.Builder = Notification.Builder(App.ctx(), id)
    }

    const val ID_PERSISTENT_FG_SERVICE = 1
    const val ID_PLAY_STORE_PENDING_USER_ACTION = 2
    const val ID_PLAY_STORE_MISSING_OBB_PERMISSION = 3
    const val ID_GMS_CORE_MISSING_NEARBY_DEVICES_PERMISSION = 4
    const val ID_MISSING_PLAY_GAMES_APP = 5

    private val uniqueNotificationId = AtomicInteger(10_000)
    fun generateUniqueNotificationId() = uniqueNotificationId.getAndIncrement()


    fun cancel(id: Int) {
        App.notificationManager().cancel(id)
    }

    @JvmStatic
    fun createNotificationChannels(ctx: Context) {
        val list = Channel.values().map {
            NotificationChannel(it.id, ctx.getText(it.title), it.importance)
        }
        App.notificationManager().createNotificationChannels(list)
    }

    fun configurationRequired(channel: Channel,
            title: CharSequence, text: CharSequence,
            resolutionText: CharSequence, resolutionIntent: Intent): Notification.Builder
    {
        val ctx = App.ctx()
        val pendingIntent = PendingIntent.getActivity(ctx, 0, freshActivity(resolutionIntent), PendingIntent.FLAG_IMMUTABLE)

        val resolution = Notification.Action.Builder(null, resolutionText, pendingIntent).build()

        return channel.notifBuilder()
            .setSmallIcon(R.drawable.ic_configuration_required)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setOnlyAlertOnce(true)
            .addAction(resolution)
    }
}

fun Notification.Builder.show(id: Int) {
    App.notificationManager().notify(id, this.build())
}

