package app.grapheneos.gmscompat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.compat.gms.GmsCompat
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import app.grapheneos.gmscompat.App.MainProcessPrefs
import app.grapheneos.gmscompat.util.PendingAction
import com.android.internal.gmscompat.GmsInfo
import java.util.concurrent.atomic.AtomicInteger

object Notifications {
    const val CH_PERSISTENT_FG_SERVICE = "persistent_fg_service"
    const val CH_PLAY_STORE_PENDING_USER_ACTION = "play_store_pending_user_action"
    const val CH_MISSING_PERMISSION = "missing_permission"
    const val CH_MISSING_OPTIONAL_PERMISSION = "missing_optional_permission"
    const val CH_MISSING_APP = "missing_app"
    // separate channel to allow silencing it without impacting other missing app prompts
    const val CH_MISSING_PLAY_GAMES_APP = "missing_play_games_app"
    const val CH_BACKGROUND_ACTIVITY_START = "bg_activity_start"
    const val CH_GMS_CRASHED = "gms_crashed"

    const val ID_PERSISTENT_FG_SERVICE = 1
    const val ID_PLAY_STORE_PENDING_USER_ACTION = 2
    const val ID_PLAY_STORE_MISSING_OBB_PERMISSION = 3
    const val ID_GMS_CORE_MISSING_NEARBY_DEVICES_PERMISSION = 4
    const val ID_MISSING_APP = 6
    const val ID_GmsCore_POWER_EXEMPTION_PROMPT = 7
    const val ID_CONTACTS_SYNC_PROMPT = 8
    const val ID_MISSING_POST_NOTIFICATIONS_PERM = 9;

    private val uniqueNotificationId = AtomicInteger(10_000)
    fun generateUniqueNotificationId() = uniqueNotificationId.getAndIncrement()

    @JvmStatic
    fun createNotificationChannels() {
        val list = listOf(
            ch(CH_PERSISTENT_FG_SERVICE, R.string.persistent_fg_service_notif),
            ch(CH_PLAY_STORE_PENDING_USER_ACTION, R.string.play_store_pending_user_action_notif),
            ch(CH_MISSING_PERMISSION, R.string.missing_permission, IMPORTANCE_HIGH),
            ch(CH_MISSING_OPTIONAL_PERMISSION, R.string.missing_optional_permission),
            ch(CH_MISSING_APP, R.string.missing_app, IMPORTANCE_HIGH),
            ch(CH_MISSING_PLAY_GAMES_APP, R.string.missing_play_games_app, IMPORTANCE_HIGH),
            ch(CH_BACKGROUND_ACTIVITY_START, R.string.notif_channel_bg_activity_start, IMPORTANCE_HIGH),
            ch(CH_GMS_CRASHED, R.string.notif_gms_crash_title, IMPORTANCE_HIGH),
        )

        App.notificationManager().createNotificationChannels(list)
    }

    private fun ch(id: String, title: Int, importance: Int = NotificationManager.IMPORTANCE_LOW,
                   silent: Boolean = true): NotificationChannel {
        val c = NotificationChannel(id, App.ctx().getText(title), importance)
        if (silent) {
            c.setSound(null, null)
            c.enableVibration(false)
        }
        return c
    }

    fun configurationRequired(channel: String,
            title: CharSequence, text: CharSequence,
            resolutionText: CharSequence, resolutionIntent: Intent): Notification.Builder
    {
        val pendingIntent = activityPendingIntent(resolutionIntent)
        val resolution = Notification.Action.Builder(null, resolutionText, pendingIntent).build()

        return builder(channel)
            .setSmallIcon(R.drawable.ic_configuration_required)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .addAction(resolution)
    }

    @JvmStatic
    fun builder(channel: String) = Notification.Builder(App.ctx(), channel)

    fun cancel(id: Int) {
        App.notificationManager().cancel(id)
    }

    private var handledGmsCorePowerExemption = false

    fun handleGmsCorePowerExemption() {
        if (handledGmsCorePowerExemption) {
            return
        }
        handledGmsCorePowerExemption = true

        val ctx = App.ctx()

        if (App.preferences().getBoolean(MainProcessPrefs.GmsCore_POWER_EXEMPTION_PROMPT_DISMISSED, false)) {
            return
        }

        val powerM = ctx.getSystemService(PowerManager::class.java)!!

        if (powerM.isIgnoringBatteryOptimizations(GmsInfo.PACKAGE_GMS_CORE)) {
            return
        }

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.fromParts("package", GmsInfo.PACKAGE_GMS_CORE, null)

        val dontShowAgainPa = PendingAction.addOneShot {
            App.preferences().edit()
                    .putBoolean(MainProcessPrefs.GmsCore_POWER_EXEMPTION_PROMPT_DISMISSED, true)
                    .apply()

            cancel(ID_GmsCore_POWER_EXEMPTION_PROMPT)
        }

        val dontShowAgainAction = Notification.Action.Builder(null,
                ctx.getText(R.string.dont_show_again), dontShowAgainPa.pendingIntent).build()

        builder(CH_MISSING_OPTIONAL_PERMISSION).apply {
            setSmallIcon(R.drawable.ic_configuration_required)
            setContentTitle(R.string.missing_optional_permission)
            setContentText(R.string.notif_gmscore_power_exemption)
            setStyle(Notification.BigTextStyle())
            setContentIntent(activityPendingIntent(intent))
            setAutoCancel(true)
            addAction(dontShowAgainAction)
            show(ID_GmsCore_POWER_EXEMPTION_PROMPT)
        }
    }

    fun handleMissingApp(channel: String, prompt: CharSequence, appPkg: String) {
        if (isPkgInstalled(appPkg)) {
            return
        }

        val ctx = App.ctx()

        if (!GmsCompat.isGmsApp(GmsInfo.PACKAGE_PLAY_STORE, ctx.userId)) {
            return
        }

        val uri = Uri.parse("market://details?id=$appPkg")
        val resolution = Intent(Intent.ACTION_VIEW, uri)
        resolution.setPackage(GmsInfo.PACKAGE_PLAY_STORE)
        configurationRequired(
                channel,
                ctx.getText(R.string.missing_app),
                prompt,
                ctx.getText(R.string.install),
                resolution
        ).show(ID_MISSING_APP)
    }

    // returns null if "do not show again" is already set for this notificationId
    fun doNotShowAgainAction(actionId: Int, notifId: Int = actionId, prefSuffix: String = ""): Notification.Action? {
        val pref = MainProcessPrefs.NOTIFICATION_DO_NOT_SHOW_AGAIN_PREFIX +
                actionId + prefSuffix

        if (App.preferences().getBoolean(pref, false)) {
            return null
        }

        val pa = PendingAction.addOneShot {
            App.preferences().edit()
                    .putBoolean(pref, true)
                    .apply()

            cancel(notifId)
        }

        return Notification.Action.Builder(null, App.ctx().getText(R.string.dont_show_again),
                pa.pendingIntent).build()
    }

    private var handledContactsSync = false

    fun handleContactsSync() {
        if (handledContactsSync) {
            return
        }
        handledContactsSync = true

        val id = ID_CONTACTS_SYNC_PROMPT

        val doNotShowAgainAction = doNotShowAgainAction(id)

        if (doNotShowAgainAction == null) {
            return
        }

        builder(CH_MISSING_OPTIONAL_PERMISSION).apply {
            setSmallIcon(R.drawable.ic_configuration_required)
            setContentTitle(R.string.missing_optional_permission)
            setContentText(R.string.notif_contacts_sync_prompt)
            setStyle(Notification.BigTextStyle())
            setContentIntent(appSettingsPendingIntent(GmsInfo.PACKAGE_GMS_CORE, APP_INFO_ITEM_PERMISSIONS))
            setAutoCancel(true)
            addAction(doNotShowAgainAction)
            show(id)
        }
    }
}

fun Notification.Builder.setContentTitle(resId: Int) {
    setContentTitle(App.ctx().getText(resId))
}

fun Notification.Builder.setContentText(resId: Int) {
    setContentText(App.ctx().getText(resId))
}

fun Notification.Builder.show(id: Int) {
    App.notificationManager().notify(id, this.build())
}
