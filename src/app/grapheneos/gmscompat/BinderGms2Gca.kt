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
import android.os.Binder
import android.os.BinderDef
import android.os.Bundle
import android.os.DeadObjectException
import android.os.Handler
import android.os.IBinder
import android.os.Messenger
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
import com.android.internal.gmscompat.GmsInfo.PACKAGE_PLAY_STORE
import com.android.internal.gmscompat.IGca2Gms
import com.android.internal.gmscompat.IGms2Gca
import com.android.internal.gmscompat.dynamite.server.IFileProxyService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.Predicate
import kotlin.collections.ArrayList

object BinderGms2Gca : IGms2Gca.Stub() {
    val ctx: Context = App.ctx()

    class BoundProcess(val iGca2Gms: IGca2Gms, val pid: Int, val uid: Int, val pkgName: String, val processName: String)

    private val boundProcesses = ArrayMap<IGca2Gms, BoundProcess>(10)

    @Volatile
    private var config = GmsCompatConfigParser.exec(App.ctx())

    override fun connect(pkg: String, processName: String, iGca2Gms: IGca2Gms): GmsCompatConfig {
        val boundProcess = BoundProcess(iGca2Gms, Binder.getCallingPid(), Binder.getCallingUid(),
                                        pkg, processName)

        val deathRecipient = DeathRecipient(iGca2Gms)
        try {
            // important to add before linkToDeath() to avoid race with binderDied() callback
            addBoundProcess(iGca2Gms, boundProcess)
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

    fun addBoundProcess(iGca2Gms: IGca2Gms, boundProcess: BoundProcess) {
        synchronized(boundProcesses) {
            boundProcesses.put(iGca2Gms, boundProcess)
        }
    }

    fun removeBoundProcess(iGca2Gms: IGca2Gms) {
        synchronized(boundProcesses) {
            val bp = boundProcesses.remove(iGca2Gms)

            if (boundProcesses.size == 0) {
                val ctx = App.ctx()
                val i = Intent(ctx, PersistentFgService::class.java)
                if (ctx.stopService(i)) {
                    logd{"no bound processes, stopping PersistentFgService"}
                }
            }

            when (bp?.processName) {
                GmsInfo.PACKAGE_PLAY_STORE -> {
                    Notifications.cancel(Notifications.ID_PLAY_STORE_MISSING_OBB_PERMISSION)
                }
                GmsHooks.PERSISTENT_GmsCore_PROCESS -> {
                    Notifications.cancel(Notifications.ID_GMS_CORE_MISSING_NEARBY_DEVICES_PERMISSION)
                }
            }
        }
    }

    fun snapshotBoundProcessses(predicate: Predicate<BoundProcess>? = null): List<IGca2Gms> {
        synchronized(boundProcesses) {
            val size = boundProcesses.size
            val res = ArrayList<IGca2Gms>(size)
            for (i in 0 until size) {
                val bp = boundProcesses.valueAt(i)
                if (predicate == null || predicate.test(bp)) {
                    res.add(boundProcesses.keyAt(i))
                }

            }
            return res
        }
    }

    fun getPersistentGmsCoreProcess(): IGca2Gms? {
        return getBoundProcessByName(GmsHooks.PERSISTENT_GmsCore_PROCESS)
    }

    fun getBoundProcessByName(name: String): IGca2Gms? {
        val processes: ArrayMap<IGca2Gms, BoundProcess> = boundProcesses
        synchronized(processes) {
            for (i in 0 until processes.size) {
                if (processes.valueAt(i).processName == name) {
                    return processes.keyAt(i)
                }
            }
        }
        return null
    }

    fun parseAndUpdateConfig() {
        updateConfig(GmsCompatConfigParser.exec(App.ctx()))
    }

    private fun updateConfig(config: GmsCompatConfig) {
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
            } catch (e: Throwable) {
                // DeadObjectException if GmsCore racily died, or another exception if actual invalidation
                // failed. This may happen when GSF is being updated, which causes
                // invalidation of Gservices to throw due to GSF being frozen during update
                logd{e}
            }
        }

        if (!invalidated) {
            logd{"unable to invalidate config caches, restarting to apply config update"}
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

    const val MAIN_PLAY_STORE_PROCESS: String = PACKAGE_PLAY_STORE
    val playStorePendingUserActionIntents = ArrayDeque<Intent>()

    override fun onPlayStorePendingUserAction(actionIntent: Intent, pkgName: String?) {
        val mainProcess = getBoundProcessByName(MAIN_PLAY_STORE_PROCESS)
        if (mainProcess != null) {
            try {
                if (mainProcess.startActivityIfVisible(actionIntent)) {
                    return
                }
            } catch (e: RemoteException) {
                // main process can racily die
                e.printStackTrace()
            }
        }

        val ctx = App.ctx()
        val launchIntent = ctx.packageManager.getLaunchIntentForPackage(PACKAGE_PLAY_STORE)
        val contentIntent = PendingIntent.getActivity(ctx, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE)

        var text = R.string.pending_play_store_update_notif_text
        val title = when (pkgName) {
            PACKAGE_GMS_CORE -> ctx.getString(R.string.pending_play_store_update_notif_title, ctx.getString(R.string.play_services))
            PACKAGE_PLAY_STORE -> ctx.getString(R.string.pending_play_store_update_notif_title, ctx.getString(R.string.play_store))
            else -> {
                text = 0
                ctx.getText(R.string.play_store_pending_user_action_notif)
            }
        }

        Notifications.builder(Notifications.CH_PLAY_STORE_PENDING_USER_ACTION).run {
            setSmallIcon(R.drawable.ic_pending_action)
            setContentTitle(title)
            if (text != 0) {
                setContentText(text)
            }
            setContentIntent(contentIntent)
            setAutoCancel(true)
            show(Notifications.ID_PLAY_STORE_PENDING_USER_ACTION)
        }

        synchronized(playStorePendingUserActionIntents) {
            playStorePendingUserActionIntents.addLast(actionIntent)
        }
    }

    override fun maybeGetPlayStorePendingUserActionIntent(): Intent? {
        synchronized(playStorePendingUserActionIntents) {
            if (playStorePendingUserActionIntents.size == 1) {
                // intentionally inside a criticat section to prevent race with addLast() above
                Notifications.cancel(Notifications.ID_PLAY_STORE_PENDING_USER_ACTION)
            }
            return playStorePendingUserActionIntents.removeLastOrNull()
        }
    }

    override fun maybeGetBinderDef(callerPkg: String, processState: Int, ifaceName: String): BinderDef? {
        return BinderDefs.maybeGetBinderDef(callerPkg, processState, ifaceName, true)
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
                .setContentTitle(ctx.getString(R.string.notif_bg_activity_start, getApplicationLabel(ctx, callerPkg)))
                .setContentIntent(intent)
                .setAutoCancel(true)
                .show(Notifications.generateUniqueNotificationId())
    }

    override fun showGmsCoreMissingPermissionForNearbyShareNotification() {
        val text = ctx.getText(R.string.missing_permission_nearby_NearbyShare)
        showGmsCoreMissingNearbyDevicesPermission(text)
    }

    override fun showGmsCoreMissingNearbyDevicesPermissionGeneric() {
         val text = ctx.getText(R.string.notif_gmscore_missing_nearby_devices_perm_generic)
         showGmsCoreMissingNearbyDevicesPermission(text)
    }

    private fun showGmsCoreMissingNearbyDevicesPermission(text: CharSequence) {
        val ctx = App.ctx();

        Notifications.configurationRequired(
                Notifications.CH_MISSING_PERMISSION,
                ctx.getText(R.string.missing_permission),
                text,
                ctx.getText(R.string.open_settings),
                appSettingsIntent(PACKAGE_GMS_CORE, APP_INFO_ITEM_PERMISSIONS)
        ).show(Notifications.ID_GMS_CORE_MISSING_NEARBY_DEVICES_PERMISSION)
    }

    override fun maybeShowContactsSyncNotification() {
        Notifications.handleContactsSync()
    }

    override fun onUncaughtException(aer: ApplicationErrorReport) {
        val TAG = "onGmsUncaughtException"
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

        if (checkForConfigUpdate) {
            requestConfigUpdate{ configUpdated ->
                Log.d(TAG, "callback from Apps: configUpdated: $configUpdated")
                if (configUpdated && showNotification) {
                    Log.d(TAG, "suppressed notification for crash that happened when config was out-of-date")
                    return@requestConfigUpdate
                }
                if (showNotification) {
                    showGmsCrashNotification(aer)
                }
            }

            if (showNotification) {
                Log.d(TAG, "delaying notification until callback from Apps")
                return
            }
        }

        if (showNotification) {
            showGmsCrashNotification(aer)
        }
    }

    override fun requestConfigUpdate(reason: String): GmsCompatConfig {
        val TAG = "requestConfigUpdate"
        Log.d(TAG, "reason: $reason")

        val sq = SynchronousQueue<Boolean>()
        requestConfigUpdate { configUpdated ->
            sq.put(configUpdated)
        }
        // block until request completes
        val configUpdated = sq.take()
        Log.d(TAG, "configUpdated: $configUpdated")

        // Config update (if it happened) will be broadcast to all bound GMS apps,
        // but that may happen after return of this method, there's no ordering guarantee.
        // Return config directly to avoid this race condition.
        return GmsCompatConfigParser.exec(App.ctx())
    }

    private fun requestConfigUpdate(configUpdateCallback: Consumer<Boolean>) {
        val ctx = App.ctx()
        val cr = ctx.contentResolver
        val authority = "app.grapheneos.apps.RpcProvider"
        val method = "update_package"
        val pkgName = ConfigUpdateReceiver.CONFIG_HOLDER_PACKAGE

        // otherwise synchronous waiting for callback would deadlock
        check(Thread.currentThread() !== App.mainThread())

        val handler = Handler(ctx.mainLooper) { msg ->
            val configUpdated = msg.arg1 != 0
            configUpdateCallback.accept(configUpdated)
            return@Handler true
        }

        val callback = Messenger(handler)
        val extras = Bundle().apply {
            putParcelable("callback", callback)
        }

        try {
            cr.call(authority, method, pkgName, extras)
        } catch (e: Exception) {
            Log.d("requestConfigUpdate", "unable to call " + authority, e)
        }
    }

    fun showGmsCrashNotification(aer: ApplicationErrorReport) {
        val ctx = App.ctx()

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

        Notifications.builder(Notifications.CH_GMS_CRASHED).run {
            setContentTitle(ctx.getText(R.string.notif_gms_crash_title))
            setContentText(ctx.getText(R.string.notif_gms_crash_text))
            setContentIntent(activityPendingIntent(intent))
            setShowWhen(true)
            setAutoCancel(true)
            setSmallIcon(R.drawable.ic_crash_report)
            addAction(reportAction)
            show(Notifications.generateUniqueNotificationId())
        }
    }

    private var prevUeNotifStackTraceId: String? = null
    private var prevUeNotifTimestamp = 0L
    private var prevConfigUpdateCheckTimestamp = 0L

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

    override fun getMediaProjectionNotification(): Notification {
        return Notification.Builder(App.ctx(), "cast_system_mirroring_service").run {
            setSmallIcon(R.drawable.ic_screen_share)
            setContentTitle(R.string.notif_screen_capture_is_running)
            build()
        }
    }

    val missingPostNotifsNotifIds = ArrayMap<String, Int>()

    override fun showMissingPostNotifsPermissionNotification(callerPkg: String) {
        val notifId = synchronized(missingPostNotifsNotifIds) {
            missingPostNotifsNotifIds.getOrPut(callerPkg) {
                Notifications.generateUniqueNotificationId()
            }
        }

        val doNotShowAgain = Notifications.doNotShowAgainAction(
                Notifications.ID_MISSING_POST_NOTIFICATIONS_PERM, notifId, callerPkg)

        if (doNotShowAgain == null) {
            return
        }

        val text = ctx.getString(R.string.notif_missing_nofications_perm,
                getApplicationLabel(ctx, callerPkg))

        Notifications.configurationRequired(
                Notifications.CH_MISSING_PERMISSION,
                ctx.getText(R.string.missing_permission),
                text,
                ctx.getText(R.string.open_settings),
                notificationSettingsIntent(callerPkg)
        ).run {
            addAction(doNotShowAgain)
            show(notifId)
        }
    }
}
