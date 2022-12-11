package app.grapheneos.gmscompat

import android.app.ApplicationErrorReport
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.IContentObserver
import android.net.Uri
import android.os.DeadObjectException
import android.os.IBinder
import android.os.RemoteException
import android.os.SystemClock
import android.provider.Settings
import android.util.ArrayMap
import android.util.Log
import app.grapheneos.gmscompat.GmsCompatConfigParser.configHolderInfo
import com.android.internal.gmscompat.GmsCompatConfig
import com.android.internal.gmscompat.GmsHooks
import com.android.internal.gmscompat.GmsInfo
import com.android.internal.gmscompat.GmsInfo.PACKAGE_GMS_CORE
import com.android.internal.gmscompat.IGca2Gms
import com.android.internal.gmscompat.IGms2Gca
import com.android.internal.gmscompat.dynamite.server.IFileProxyService
import java.util.*
import java.util.concurrent.TimeUnit

object BinderGms2Gca : IGms2Gca.Stub() {
    private val boundProcesses = ArrayMap<IGca2Gms, String>(10)

    @Volatile
    private var config = GmsCompatConfigParser.exec(App.ctx())

    override fun connect(pkg: String, processName: String, iGca2Gms: IGca2Gms): GmsCompatConfig {
        val deathRecipient = DeathRecipient(iGca2Gms)
        try {
            // important to add before linkToDeath() to avoid race with binderDied() callback
            addBoundProcess(iGca2Gms, processName)
            iGca2Gms.asBinder().linkToDeath(deathRecipient, 0)
        } catch (e: RemoteException) {
            logd{"binder already died: " + e}
            deathRecipient.binderDied()
            throw e
        }
        PersistentFgService.start(pkg, processName);

        // Config holder update event might be delivered after GMS process starts if both
        // GMS component and GmsCompatConfig holder are updated together, atomically.
        // This would lead to crashes or errors if the new version of GMS component
        // (most llkely GmsCore) depends on the updated GmsCompatConfig.
        val ctx = App.ctx()
        if (config.version != configHolderInfo(ctx).longVersionCode) {
            config = GmsCompatConfigParser.exec(ctx)
        }

        return config
    }

    class DeathRecipient(val iGca2Gms: IGca2Gms) : IBinder.DeathRecipient {
        override fun binderDied() {
            removeBoundProcess(iGca2Gms)
        }
    }

    fun addBoundProcess(iGca2Gms: IGca2Gms, processName: String) {
        synchronized(boundProcesses) {
            boundProcesses.put(iGca2Gms, processName)
        }
    }

    fun removeBoundProcess(iGca2Gms: IGca2Gms) {
        synchronized(boundProcesses) {
            val processName = boundProcesses.remove(iGca2Gms)

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
                GmsHooks.PERSISTENT_GmsCore_PROCESS -> {
                    Notifications.cancel(Notifications.ID_GMS_CORE_MISSING_NEARBY_DEVICES_PERMISSION)
                }
            }
        }
    }

    fun snapshotBoundProcessses(): Array<IGca2Gms> {
        var res: Array<IGca2Gms?>
        synchronized(boundProcesses) {
            val size = boundProcesses.size
            res = arrayOfNulls(size)
            for (i in 0 until size) {
                res[i] = boundProcesses.keyAt(i)
            }
        }
        @Suppress("UNCHECKED_CAST")
        return res as Array<IGca2Gms>
    }

    fun getPersistentGmsCoreProcess(): IGca2Gms? {
        val processes: ArrayMap<IGca2Gms, String> = boundProcesses
        synchronized(processes) {
            for (i in 0 until processes.size) {
                if (processes.valueAt(i) == GmsHooks.PERSISTENT_GmsCore_PROCESS) {
                    return processes.keyAt(i)
                }
            }
        }
        return null
    }

    fun updateConfig(config: GmsCompatConfig) {
        this.config = config

        snapshotBoundProcessses().forEach {
            try {
                it.updateConfig(config)
            } catch (e: DeadObjectException) {
                logd{e}
            }
        }

        val p = getPersistentGmsCoreProcess()
        var invalidated = false
        if (p != null) {
            try {
                p.invalidateConfigCaches()
                invalidated = true
            } catch (e: DeadObjectException) {
                logd{e}
            }
        }

        if (!invalidated) {
            logd{"persistent GmsCore process not found, restarting to apply config update"}
            // all bound GMS processes will exit too
            System.exit(0)
        }
    }

    @Volatile
    var dynamiteFileProxyService: IFileProxyService? = null

    override fun connectGmsCore(processName: String, iGca2Gms: IGca2Gms, fileProxyService: IFileProxyService?): GmsCompatConfig {
        if (fileProxyService != null) {
            dynamiteFileProxyService = fileProxyService
        }

        if (processName == GmsHooks.PERSISTENT_GmsCore_PROCESS) {
            App.ctx().mainExecutor.execute {
                Notifications.handleGmsCorePowerExemption()
            }
        }

        return connect(PACKAGE_GMS_CORE, processName, iGca2Gms)
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

        val uri = Uri.fromParts("package", GmsInfo.PACKAGE_PLAY_STORE, null)
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri)

        Notifications.configurationRequired(
                Notifications.CH_MISSING_PERMISSION,
                ctx.getText(R.string.missing_permission),
                ctx.getText(R.string.play_store_missing_obb_permission_notif),
                ctx.getText(R.string.open_settings),
                intent
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
                appSettingsIntent(PACKAGE_GMS_CORE, APP_INFO_ITEM_PERMISSIONS)
        ).show(Notifications.ID_GMS_CORE_MISSING_NEARBY_DEVICES_PERMISSION)
    }

    override fun showGmsMissingNearbyDevicesPermissionGeneric(callerPkg: String) {
        val ctx = App.ctx()
        Notifications.configurationRequired(
                Notifications.CH_MISSING_PERMISSION,
                ctx.getText(R.string.missing_permission),
                ctx.getString(R.string.notif_missing_nearby_devices_perm_generic, applicationLabel(ctx, callerPkg)),
                ctx.getText(R.string.open_settings),
                appSettingsIntent(callerPkg, APP_INFO_ITEM_PERMISSIONS)
        ).show(Notifications.ID_MISSING_NEARBY_DEVICES_PERMISSION_GENERIC)
    }

    override fun maybeShowContactsSyncNotification() {
        Notifications.handleContactsSync()
    }

    override fun onUncaughtException(aer: ApplicationErrorReport) {
        val ts = SystemClock.elapsedRealtime()
        val stackTrace = aer.crashInfo.stackTrace

        // Don't spam notifications if GMS chain-crashes with similar stack traces.

        // Same privileged method call may crash with different full stack traces, check only
        // the first few lines of the stack trace for equality
        val stackTraceId = stackTrace.lines().filter { it.startsWith("\tat") }.take(5).joinToString()

        var checkForConfigUpdate = false
        var showNotification = true

        synchronized(this) {
            if (ts - prevConfigUpdateCheckTimestamp > TimeUnit.MINUTES.toMillis(5)) {
                prevConfigUpdateCheckTimestamp = ts
                checkForConfigUpdate = true
            }

            if (stackTraceId == prevUeNotifStackTraceId) {
                val prev = prevUeNotifTimestamp
                if (prev != 0L && (ts - prev) < TimeUnit.MINUTES.toMillis(10)) {
                    showNotification = false
                }
            }
            if (showNotification) {
                prevUeNotifStackTraceId = stackTraceId
                prevUeNotifTimestamp = ts
            }
        }

        val ctx = App.ctx()

        if (checkForConfigUpdate) {
            val cr = ctx.contentResolver
            val authority = "app.grapheneos.apps.RpcProvider"
            val method = "gmscompat_config_update_check"
            try {
                cr.call(authority, method, null, null)
            } catch (e: Exception) {
                Log.d("UncaughtExceptionInGms", "unable to call " + authority, e)
            }
        }

        if (!showNotification) {
            return
        }

        val intent = Intent(Intent.ACTION_APP_ERROR)
        intent.putExtra(Intent.EXTRA_BUG_REPORT, aer)
        val configVersion = ctx.packageManager.getPackageInfo(ConfigUpdateReceiver.CONFIG_HOLDER_PACKAGE,
                PackageManager.PackageInfoFlags.of(0L)).longVersionCode
        intent.putExtra(Intent.EXTRA_TEXT, "GmsCompatConfig version: $configVersion")
        intent.setComponent(ComponentName.createRelative("com.android.systemui", ".ErrorReportActivity"))

        val reportAction = run {
            val url = "https://github.com/GrapheneOS/os-issue-tracker/issues"
            val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            val label = ctx.getText(R.string.notif_gms_crash_report)
            Notification.Action.Builder(null, label, activityPendingIntent(urlIntent)).build()
        }

        Notifications.builder(Notifications.CH_GMS_CRASHED).apply {
            setContentTitle(ctx.getText(R.string.notif_gms_crash_title))
            setContentText(ctx.getText(R.string.notif_gms_crash_text))
            setContentIntent(activityPendingIntent(intent))
            setShowWhen(true)
            setAutoCancel(true)
            setSmallIcon(R.drawable.ic_crash_report)
            addAction(reportAction)
        }.show(Notifications.generateUniqueNotificationId())
    }

    private var prevUeNotifStackTraceId: String? = null
    private var prevUeNotifTimestamp = 0L
    private var prevConfigUpdateCheckTimestamp = 0L

    private fun applicationLabel(ctx: Context, pkg: String): CharSequence {
        val pm = ctx.packageManager
        return pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0)))
    }

    private val privSettings = PrivSettings()

    override fun privSettingsGetString(ns: String, key: String): String? {
        return privSettings.getString(ns, key)
    }

    override fun privSettingsPutString(ns: String, key: String, value: String?): Boolean {
        return privSettings.putString(ns, key, value)
    }

    override fun privSettingsPutStrings(ns: String, keys: Array<String>, values: Array<String>): Boolean {
        return privSettings.putStrings(ns, keys, values)
    }

    override fun privSettingsRegisterObserver(ns: String, key: String, observer: IContentObserver) {
        privSettings.addObserver(ns, key, observer)
    }

    override fun privSettingsUnregisterObserver(observer: IContentObserver) {
        privSettings.removeObserver(observer)
    }
}
