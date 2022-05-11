package app.grapheneos.gmscompat

import android.app.AppOpsManager
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import app.grapheneos.gmscompat.Const.DEV
import app.grapheneos.gmscompat.Const.ENABLE_LOGGING
import com.android.internal.gmscompat.GmsCompatApp
import com.android.internal.gmscompat.GmsInfo
import java.lang.StringBuilder
import java.lang.reflect.Modifier

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
        App.ctx().packageManager.getPackageInfo(pkg, 0)
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

fun appSettingsIntent(pkg: String): Intent {
    val uri = Uri.fromParts("package", pkg, null)
    return freshActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
}

fun gmsCoreSettings() = appSettingsIntent(GmsInfo.PACKAGE_GMS_CORE)
fun playStoreSettings() = appSettingsIntent(GmsInfo.PACKAGE_PLAY_STORE)
