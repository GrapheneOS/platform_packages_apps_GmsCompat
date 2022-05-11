package app.grapheneos.gmscompat

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.RemoteException
import android.util.ArrayMap
import com.android.internal.gmscompat.GmsInfo
import com.android.internal.gmscompat.IGms2Gca
import com.android.internal.gmscompat.dynamite.server.IFileProxyService

object BinderGms2Gca : IGms2Gca.Stub() {
    private val boundProcesses = ArrayMap<IBinder, String>(10)

    fun connect(pkg: String, processName: String, callerBinder: IBinder) {
        val deathRecipient = DeathRecipient(callerBinder)
        try {
            // important to add before linkToDeath() to avoid race with binderDied() callback
            addBoundProcess(callerBinder, processName)
            callerBinder.linkToDeath(deathRecipient, 0)
        } catch (e: RemoteException) {
            logd{"binder already died: " + e}
            deathRecipient.binderDied()
            return
        }
        PersistentFgService.start(pkg, processName);
    }

    class DeathRecipient(val binder: IBinder) : IBinder.DeathRecipient {
        override fun binderDied() {
            removeBoundProcess(binder)
        }
    }

    fun addBoundProcess(binder: IBinder, processName: String) {
        synchronized(boundProcesses) {
            boundProcesses.put(binder, processName)
        }
    }

    fun removeBoundProcess(binder: IBinder) {
        synchronized(boundProcesses) {
            val processName = boundProcesses.remove(binder)

            if (boundProcesses.size == 0) {
                val ctx = App.ctx()
                val i = Intent(ctx, PersistentFgService::class.java)
                if (ctx.stopService(i)) {
                    logd{"no bound processes, stopping PersistentFgService"}
                }
            }

            when (processName) {
                GmsInfo.PACKAGE_PLAY_STORE -> {
                    dismissPlayStorePendingUserActionNotification()
                    Notifications.cancel(Notifications.ID_PLAY_STORE_MISSING_OBB_PERMISSION)
                }
                "com.google.android.gms.persistent" -> {
                    Notifications.cancel(Notifications.ID_GMS_CORE_MISSING_NEARBY_DEVICES_PERMISSION)
                }
            }
        }
    }

    override fun connectGsf(processName: String, callerBinder: IBinder) {
        connect(GmsInfo.PACKAGE_GSF, processName, callerBinder)
    }

    @Volatile
    var dynamiteFileProxyService: IFileProxyService? = null

    override fun connectGmsCore(processName: String, callerBinder: IBinder, fileProxyService: IFileProxyService?) {
        if (fileProxyService != null) {
            dynamiteFileProxyService = fileProxyService
        }
        connect(GmsInfo.PACKAGE_GMS_CORE, processName, callerBinder)
    }

    override fun connectPlayStore(processName: String, callerBinder: IBinder) {
        connect(GmsInfo.PACKAGE_PLAY_STORE, processName, callerBinder)
    }

    override fun showPlayStorePendingUserActionNotification() {
        val ctx = App.ctx()
        val intent = ctx.packageManager.getLaunchIntentForPackage(GmsInfo.PACKAGE_PLAY_STORE)
        val pendingIntent = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        Notifications.builder(Notifications.CH_PLAY_STORE_PENDING_USER_ACTION)
                .setSmallIcon(R.drawable.ic_pending_action)
                .setContentTitle(ctx.getText(R.string.play_store_pending_user_action_notif))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .show(Notifications.ID_PLAY_STORE_PENDING_USER_ACTION)
    }

    override fun dismissPlayStorePendingUserActionNotification() {
        Notifications.cancel(Notifications.ID_PLAY_STORE_PENDING_USER_ACTION)
    }

    override fun showPlayStoreMissingObbPermissionNotification() {
        val ctx = App.ctx()

        Notifications.configurationRequired(
                Notifications.CH_MISSING_PERMISSION,
                ctx.getText(R.string.missing_permission),
                ctx.getText(R.string.play_store_missing_obb_permission_notif),
                ctx.getText(R.string.open_settings),
                playStoreSettings()
        ).show(Notifications.ID_PLAY_STORE_MISSING_OBB_PERMISSION)
    }

    override fun startActivityFromTheBackground(callerPkg: String, intent: PendingIntent) {
        val ctx = App.ctx()
        Notifications.builder(Notifications.CH_BACKGROUND_ACTIVITY_START)
                .setSmallIcon(R.drawable.ic_configuration_required)
                .setContentTitle(ctx.getString(R.string.notif_bg_activity_start, applicationLabel(ctx, callerPkg)))
                .setContentIntent(intent)
                .setAutoCancel(true)
                .show(Notifications.generateUniqueNotificationId())
    }

    override fun showGmsCoreMissingPermissionForNearbyShareNotification() {
        val ctx = App.ctx();

        Notifications.configurationRequired(
                Notifications.CH_MISSING_PERMISSION,
                ctx.getText(R.string.missing_permission),
                ctx.getText(R.string.missing_permission_nearby_NearbyShare),
                ctx.getText(R.string.open_settings),
                gmsCoreSettings()
        ).show(Notifications.ID_GMS_CORE_MISSING_NEARBY_DEVICES_PERMISSION)
    }

    override fun showGmsMissingNearbyDevicesPermissionGeneric(callerPkg: String) {
        val ctx = App.ctx()
        Notifications.configurationRequired(
                Notifications.CH_MISSING_PERMISSION,
                ctx.getText(R.string.missing_permission),
                ctx.getString(R.string.notif_missing_nearby_devices_perm_generic, applicationLabel(ctx, callerPkg)),
                ctx.getText(R.string.open_settings),
                appSettingsIntent(callerPkg)
        ).show(Notifications.ID_MISSING_NEARBY_DEVICES_PERMISSION_GENERIC)
    }

    private fun applicationLabel(ctx: Context, pkg: String): CharSequence {
        val pm = ctx.packageManager
        return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))
    }
}
