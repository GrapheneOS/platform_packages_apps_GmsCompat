package app.grapheneos.gmscompat

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.os.Build.IS_DEBUGGABLE
import com.android.internal.gmscompat.GmsInfo

enum class NotableInterface(val ifaceName: String) {
    ExposureNotificationService("com.google.android.gms.nearby.exposurenotification.internal.INearbyExposureNotificationService"),
    GamesService("com.google.android.gms.games.internal.IGamesService"),
    ;

    fun onAcquiredByClient(callerPkg: String, processState: Int) {
        if (IS_DEBUGGABLE) {
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
                Notifications.handleMissingApp(Notifications.CH_MISSING_PLAY_GAMES_APP,
                        ctx.getString(R.string.missing_play_games_app),
                        "com.google.android.play.games")
            }
        }
    }
}
