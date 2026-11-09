package app.grapheneos.gmscompat

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.GosPackageState
import android.ext.settings.app.AswMissingPlayGamesNotification
import android.util.Log
import app.grapheneos.gmscompat.Const.IS_DEV_BUILD
import com.android.internal.gmscompat.GmsInfo

enum class NotableInterface(val ifaceName: String) {
    ExposureNotificationService("com.google.android.gms.nearby.exposurenotification.internal.INearbyExposureNotificationService"),
    GamesService("com.google.android.gms.games.internal.IGamesService"),
    WearableService("com.google.android.gms.wearable.internal.IWearableService"),
    ;

    fun onAcquiredByClient(callerPkg: String, processState: Int) {
        if (IS_DEV_BUILD) {
            logd{"pkgName $callerPkg, processState: ${ActivityManager.procStateToString(processState)}, ifaceName $ifaceName"}
        }

        val ctx = App.ctx()
        when (this) {
            ExposureNotificationService -> {
                if (processState > ActivityManager.PROCESS_STATE_TOP) {
                    return
                }

                if (!gmsCoreHasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                    Notifications.configurationRequired(
                            Notifications.CH_MISSING_PERMISSION,
                            ctx.getText(R.string.missing_permission),
                            ctx.getText(R.string.missing_permission_nearby_exposurenotifications),
                            ctx.getText(R.string.open_settings),
                            appSettingsIntent(GmsInfo.PACKAGE_GMS_CORE, APP_INFO_ITEM_PERMISSIONS)
                    ).show(Notifications.ID_GMS_CORE_MISSING_NEARBY_DEVICES_PERMISSION)
                }
            }
            GamesService -> {

                // TODO: Caller package is always Google Play Services so override callerPkg for now
                val callerPkg = "com.playrix.gardenscapes"
                val setting = AswMissingPlayGamesNotification.I
                val gosPs = GosPackageState.get(callerPkg, ctx.userId)
                println("NotableInterface ctx.userID: ${ctx.userId}")


                val TAG = "showMissingPlayGamesNotification"
                Log.d(TAG, "callerPkg: $callerPkg")

                if (!setting.isNotificationEnabled(gosPs)) {
                    Log.e(TAG, "notification is disabled")
                    return
                }

                Notifications.handleMissingApp(Notifications.CH_MISSING_PLAY_GAMES_APP,
                        ctx.getString(R.string.missing_play_games_app, getApplicationLabel(ctx, callerPkg)),
                        "com.google.android.play.games", callerPkg)
            }
            WearableService -> {
                if (processState > ActivityManager.PROCESS_STATE_TOP) {
                    return
                }

                // this service is acquired by many Google apps in background and in foreground,
                // show notif only for foreground Wear OS companion apps
                when (callerPkg) {
                    // "Wear OS"
                    "com.google.android.wearable.app",
                    // "Google Pixel Watch"
                    "com.google.android.apps.wear.companion",
                        -> showGmsCoreMissingNearbyDevicesPermGeneric(ctx, callerPkg)
                }

            }
        }
    }

    fun showGmsCoreMissingNearbyDevicesPermGeneric(ctx: Context, callerPkg: String) {
        Notifications.configurationRequired(
                Notifications.CH_MISSING_PERMISSION,
                ctx.getText(R.string.missing_permission),
                ctx.getString(R.string.notif_GmsCore_missing_nearby_devices_perm_generic, getApplicationLabel(ctx, callerPkg)),
                ctx.getText(R.string.open_settings),
                appSettingsIntent(GmsInfo.PACKAGE_GMS_CORE, APP_INFO_ITEM_PERMISSIONS)
        ).show(Notifications.ID_GMS_CORE_MISSING_NEARBY_DEVICES_PERMISSION)
    }
}
