package app.grapheneos.gmscompat

import android.app.ApplicationErrorReport
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.StringParceledListSlice
import android.ext.LogViewerApp
import android.ext.PackageId
import android.net.Uri
import android.os.Binder
import android.os.BinderDef
import android.os.Bundle
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
import com.android.internal.gmscompat.GmsInfo
import com.android.internal.gmscompat.GmsInfo.PACKAGE_GMS_CORE
import com.android.internal.gmscompat.GmsInfo.PACKAGE_PLAY_STORE
import com.android.internal.gmscompat.IGms2Gca
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

object BinderGms2Gca : IGms2Gca.Stub() {
    val TAG = BinderGms2Gca::class.java.simpleName
    val ctx: Context = App.ctx()

    class BoundProcess(
        val processBinder: IBinder,
        val pid: Int,
        val uid: Int,
        val pkgName: String,
        val processName: String,
    )

    private val boundProcesses = ArrayMap<IBinder, BoundProcess>(10)

    @Volatile
    private var config = GmsCompatConfigParser.exec(ctx)

    override fun connect(packageName: String, processName: String, processBinder: IBinder): GmsCompatConfig {
        val boundProcess = BoundProcess(processBinder, Binder.getCallingPid(),
            Binder.getCallingUid(), packageName, processName)

        Log.d(TAG, "connect from $processName, pid ${boundProcess.pid}, pkg ${packageName}")

        val persistentFgServiceLatch = PersistentFgService.requestStart()
        // important to add before linkToDeath() to avoid race with binderDied() callback
        addBoundProcess(processBinder, boundProcess)
        val processDeathRecipient = DeathRecipient(processBinder)
        try {
            processBinder.linkToDeath(processDeathRecipient, 0)
        } catch (e: RemoteException) {
            logd{"binder already died: " + e}
            processDeathRecipient.binderDied()
            throw e
        }

        // Config holder update event might be delivered after GMS process starts if both
        // GMS component and GmsCompatConfig holder are updated together, atomically.
        // This would lead to crashes or errors if the new version of GMS component
        // (most llkely GmsCore) depends on the updated GmsCompatConfig.
        if (config.version != configHolderInfo(ctx).longVersionCode) {
            config = GmsCompatConfigParser.exec(ctx)
        }

        if (!persistentFgServiceLatch.await(5, TimeUnit.SECONDS)) {
            Log.e(TAG, "persistentFgServiceLatch timed out")
        }

        return config
    }

    class DeathRecipient(val processBinder: IBinder) : IBinder.DeathRecipient {
        override fun binderDied() {
            PersistentFgService.release()
            removeBoundProcess(processBinder)
        }
    }

    fun addBoundProcess(processBinder: IBinder, boundProcess: BoundProcess) {
        synchronized(boundProcesses) {
            boundProcesses.put(processBinder, boundProcess)
        }
    }

    fun removeBoundProcess(processBinder: IBinder) {
        synchronized(boundProcesses) {
            val bp = boundProcesses.remove(processBinder)

            if (bp == null) {
                Log.e(TAG, "removeBoundProcess: unknown binder")
                return
            }

            Log.d(TAG, "removeBoundProcess: ${bp.processName}, pid ${bp.pid}, pkg ${bp.pkgName}")

            when (bp.processName) {
                PackageId.PLAY_STORE_NAME -> {
                    Notifications.cancel(Notifications.ID_PLAY_STORE_MISSING_OBB_PERMISSION)
                    synchronized(playStorePendingUserActionIntents) {
                        if (!playStorePendingUserActionIntents.isEmpty()) {
                            // pending PackageInstaller sessions are cleared on Play Store startup,
                            // which invalidates pending user actions intents
                            playStorePendingUserActionIntents.clear()
                            Notifications.cancel(Notifications.ID_PLAY_STORE_PENDING_USER_ACTION)
                        }
                    }
                }
                PackageId.GMS_CORE_NAME -> {
                    Notifications.cancel(Notifications.ID_GMS_CORE_MISSING_NEARBY_DEVICES_PERMISSION)
                }
            }
        }
    }

    fun updateConfig() {
        this.config = GmsCompatConfigParser.exec(ctx)
    }

    val playStorePendingUserActionIntents = ArrayDeque<Intent>()

    override fun onPlayStorePendingUserAction(actionIntent: Intent, pkgName: String?) {
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

        synchronized(playStorePendingUserActionIntents) {
            playStorePendingUserActionIntents.addLast(actionIntent)
        }

        val showNotification = Runnable {
            synchronized(playStorePendingUserActionIntents) {
                if (playStorePendingUserActionIntents.isEmpty()) {
                    return@Runnable
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
        }
        // Play Store launches the pending action intent right away if it has a visible activity,
        // delay the notification to avoid cancelling it in that case
        ctx.mainThreadHandler.postDelayed(showNotification, 2000)
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

    override fun raisePackageToForeground(targetPkg: String, durationMs: Long, reason: String?, reasonCode: Int,
                                          serviceClassName: String?) {
        TempServiceBinding.create(targetPkg, durationMs, reason, reasonCode, serviceClassName)
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

        val stackTrace = aer.crashInfo.stackTrace

        if (aer.packageName == PackageId.ANDROID_AUTO_NAME) {
            val perm = android.Manifest.permission.REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION
            if (ctx.checkCallingPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "skipped Android Auto crash report: baseline permissions are not granted")
                showAndroidAutoMissingBaselinePermsNotif()
                return
            }
            if (stackTrace.contains("java.lang.IllegalStateException: OutOfCarLifecycle")) {
                showAndroidAutoCarConnectionLossNotif()
                return
            }
        }

        val ts = SystemClock.elapsedRealtime()

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
                Log.d(TAG, "callback from AppStore: configUpdated: $configUpdated")
                if (configUpdated && showNotification) {
                    Log.d(TAG, "suppressed notification for crash that happened when config was out-of-date")
                    return@requestConfigUpdate
                }
                if (showNotification) {
                    showGmsCrashNotification(aer)
                }
            }

            if (showNotification) {
                Log.d(TAG, "delaying notification until callback from AppStore")
                return
            }
        }

        if (showNotification) {
            showGmsCrashNotification(aer)
        }
    }

    private fun showAndroidAutoCarConnectionLossNotif() {
        Notifications.builder(Notifications.CH_APP_CRASHED).run {
            setContentTitle(ctx.getText(R.string.notif_android_auto_car_connection_lost))
            setShowWhen(true)
            setSmallIcon(R.drawable.ic_info)
            show(Notifications.generateUniqueNotificationId())
        }
    }

    private fun showAndroidAutoMissingBaselinePermsNotif() {
        Notifications.builder(Notifications.CH_APP_CRASHED).run {
            setContentTitle(ctx.getText(R.string.notif_android_auto_needs_baseline_perms_title))
            setContentText(ctx.getText(R.string.notif_android_auto_needs_baseline_perms_text))
            setShowWhen(true)
            setSmallIcon(R.drawable.ic_configuration_required)
            show(Notifications.ID_ANDROID_AUTO_NEEDS_BASELINE_PERMS)
        }
    }

    override fun getConfig(): GmsCompatConfig {
        return this.config
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
            handler.obtainMessage().sendToTarget() // Message.arg1 is 0 by default
            Log.d("requestConfigUpdate", "unable to call " + authority, e)
        }
    }

    private fun shouldIgnoreCrash(stackTrace: String): Boolean {
        // Upstream bug: FIDO activity doesn't expect NFC state to change while it's in background.
        // This crash affects GmsCore UI process while its activity is in the background, which means
        // that the crashed activity restarts automatically when the user returns to it.
        val marker1 = "Error receiving broadcast Intent { act=android.nfc.action.ADAPTER_STATE_CHANGED"
        val marker2 = "Can not perform this action after onSaveInstanceState"
        val marker3 = "com.google.android.gms.fido.u2f.api.controller.NfcBroadcastReceiver"
        return stackTrace.contains(marker1) && stackTrace.contains(marker2) && stackTrace.contains(marker3)
    }

    fun showGmsCrashNotification(aer: ApplicationErrorReport) {
        val stackTrace = aer.crashInfo?.stackTrace
        if (stackTrace != null && shouldIgnoreCrash(stackTrace)) {
            Log.d(TAG, "showGmsCrashNotification: ignored $stackTrace")
            return
        }

        val ctx = App.ctx()

        val intent = Intent(Intent.ACTION_APP_ERROR)
        intent.putExtra(Intent.EXTRA_BUG_REPORT, aer)
        val configVersion = ctx.packageManager.getPackageInfo(ConfigUpdateReceiver.CONFIG_HOLDER_PACKAGE,
                PackageManager.PackageInfoFlags.of(0L)).longVersionCode
        intent.putExtra(LogViewerApp.EXTRA_SHOW_REPORT_BUTTON, true)
        intent.putExtra(Intent.EXTRA_TEXT, "GmsCompatConfig version: $configVersion")
        intent.setPackage(LogViewerApp.getPackageName());

        val reportAction = run {
            val url = "https://github.com/GrapheneOS/os-issue-tracker/issues"
            val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            val label = ctx.getText(R.string.notif_gms_crash_report)
            Notification.Action.Builder(null, label, activityPendingIntent(urlIntent)).build()
        }

        Notifications.builder(Notifications.CH_APP_CRASHED).run {
            setContentTitle(ctx.getString(R.string.notif_gms_crash_title, getApplicationLabel(ctx, aer.packageName)))
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

    private val privSettings = PrivSettings(App.ctx())

    override fun privSettingsGetString(ns: String, key: String): String? {
        return privSettings.getString(ns, key)
    }

    override fun privSettingsPutString(ns: String, key: String, value: String?): Boolean {
        return privSettings.putString(ns, key, value)
    }

    override fun privSettingsPutStrings(ns: String, keys: StringParceledListSlice, values: StringParceledListSlice): Boolean {
        return privSettings.putStrings(ns, keys.list, values.list)
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

    override fun maybeShowGmsCorePowerExemptionNotification() {
        Notifications.handleGmsCorePowerExemption()
    }

    override fun maybeShowGmsCoreRestrictedBackgroundDataNotif() {
        Notifications.handleGmsCoreRestrictedBackgroundDataNotif()
    }

    override fun maybeShowRcsRequirementsNotification(isTs43Verification: Boolean) {
        Notifications.handleRcsNotification(isTs43Verification)
    }
}
