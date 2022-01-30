package app.grapheneos.gmscompat

import android.app.AppOpsManager
import android.util.Log
import java.lang.StringBuilder
import java.lang.reflect.Modifier

const val DEBUG = false

fun logd() {
    if (!DEBUG) {
        return
    }
    logInternal("<>", Log.DEBUG, 4)
}

fun logds(msg: String) {
    if (!DEBUG) {
        return
    }
    logInternal(msg, Log.DEBUG, 4)
}

inline fun logd(msg: () -> Any?) {
    if (!DEBUG) {
        return
    }
    logInternal(msg(), Log.DEBUG, 3)
}

inline fun log(msg: () -> Any?, level: Int) {
    if (!DEBUG) {
        return
    }
    logInternal(msg(), level, 3)
}

fun logInternal(o: Any?, level: Int, depth: Int) {
    if (!DEBUG) {
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

private fun objectToString(o: Any?): String {
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
