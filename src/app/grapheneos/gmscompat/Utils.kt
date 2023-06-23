package app.grapheneos.gmscompat

import android.app.AppOpsManager
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import app.grapheneos.gmscompat.Const.DEV
import app.grapheneos.gmscompat.Const.ENABLE_LOGGING
import com.android.internal.gmscompat.GmsCompatApp
import com.android.internal.gmscompat.GmsInfo
import java.lang.reflect.Modifier
import java.util.UUID

fun mainThread() {
    if (DEV) {
        check(Thread.currentThread() === App.mainThread())
    }
}

private fun isMainProcess() = Application.getProcessName() == GmsCompatApp.PKG_NAME

fun mainProcess() {
    if (DEV) {
        check(isMainProcess())
    }
}

fun notMainProcess() {
    if (DEV) {
        check(!isMainProcess())
    }
}

fun logd() {
    if (!ENABLE_LOGGING) {
        return
    }
    logInternal("<>", Log.DEBUG, 4)
}

fun logds(msg: String) {
    if (!ENABLE_LOGGING) {
        return
    }
    logInternal(msg, Log.DEBUG, 4)
}

inline fun logd(msg: () -> Any?) {
    if (!ENABLE_LOGGING) {
        return
    }
    logInternal(msg(), Log.DEBUG, 3)
}

inline fun logw(msg: () -> Any?) {
    if (!ENABLE_LOGGING) {
        return
    }
    logInternal(msg(), Log.WARN, 3)
}

inline fun log(msg: () -> Any?, level: Int) {
    if (!ENABLE_LOGGING) {
        return
    }
    logInternal(msg(), level, 3)
}

fun logInternal(o: Any?, level: Int, depth: Int) {
    if (!ENABLE_LOGGING) {
        return
    }
    val e = Thread.currentThread().stackTrace[depth]
    val sb = StringBuilder(100)
    sb.append(e.getMethodName())
    sb.append(" (")
    sb.append(e.getFileName())
    sb.append(':')
    sb.append(e.getLineNumber())
    sb.append(')')
    Log.println(level, sb.toString(), objectToString(o))
}

fun objectToString(o: Any?): String {
    if (o == null || o is String || o is Number || o is Boolean || o is Char) {
        return o.toString()
    }
    val b = StringBuilder(100)
    b.append(o.javaClass.name)
    b.append(" [ ")
    o.javaClass.fields.forEach {
        if (!Modifier.isStatic(it.modifiers)) {
            b.append(it.name)
            b.append(": ")
            b.append(it.get(o))
//            b.append(objectToString(it.get(o)))
            b.append(", ")
        }
    }
    b.append("]")
    return b.toString()
}

fun opModeToString(mode: Int): String =
    when (mode) {
        AppOpsManager.MODE_ALLOWED -> "MODE_ALLOWED"
        AppOpsManager.MODE_IGNORED -> "MODE_IGNORED"
        AppOpsManager.MODE_ERRORED -> "MODE_ERRORED"
        AppOpsManager.MODE_DEFAULT -> "MODE_DEFAULT"
        AppOpsManager.MODE_FOREGROUND -> "MODE_FOREGROUND"
        else -> error(mode)
    }

fun gmsCoreHasPermission(perm: String): Boolean {
    return appHasPermission(GmsInfo.PACKAGE_GMS_CORE, perm)
}

fun playStoreHasPermission(perm: String): Boolean {
    return appHasPermission(GmsInfo.PACKAGE_PLAY_STORE, perm)
}

fun appHasPermission(pkg: String, perm: String): Boolean {
    return App.ctx().packageManager.checkPermission(perm, pkg) == PackageManager.PERMISSION_GRANTED
}

fun isPkgInstalled(pkg: String): Boolean {
    try {
        App.ctx().packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        return true
    } catch (e: PackageManager.NameNotFoundException) {
        return false
    }
}

fun freshActivity(intent: Intent): Intent {
    // needed to ensure consistent behavior,
    // otherwise existing instance that is in unknown state could be shown
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
    return intent
}

const val APP_INFO_ITEM_PERMISSIONS = "permission_settings"

fun appSettingsIntent(pkg: String, item: String? = null): Intent {
    val uri = Uri.fromParts("package", pkg, null)
    val i =  freshActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
    if (item != null) {
        val args = Bundle()
        // :settings constants aren't exposed by Settings as part of its API, but are used in
        // multiple places in the OS
        args.putString(":settings:fragment_args_key", item)
        i.putExtra(":settings:show_fragment_args", args)
    }
    return i
}

fun notificationSettingsIntent(pkg: String): Intent {
    return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
    }
}

fun gmsCoreSettings() = appSettingsIntent(GmsInfo.PACKAGE_GMS_CORE)
fun playStoreSettings() = appSettingsIntent(GmsInfo.PACKAGE_PLAY_STORE)

fun appSettingsPendingIntent(pkg: String, item: String? = null) =
        activityPendingIntent(appSettingsIntent(pkg, item))

fun activityPendingIntent(i: Intent, flags: Int = PendingIntent.FLAG_IMMUTABLE): PendingIntent {
    i.setIdentifier(UUID.randomUUID().toString())
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    return PendingIntent.getActivity(App.ctx(), 0, i, flags)
}
