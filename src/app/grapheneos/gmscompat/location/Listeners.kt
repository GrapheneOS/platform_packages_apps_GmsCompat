package app.grapheneos.gmscompat.location

import android.app.AppOpsManager
import android.app.PendingIntent
import android.os.RemoteException
import android.util.ArrayMap
import android.util.Log
import app.grapheneos.gmscompat.logd
import app.grapheneos.gmscompat.opModeToString
import java.lang.IllegalStateException

// all listeners registered by a given uid. key is either Binder or PendingIntent
typealias Listeners = ArrayMap<Any, OsLocationListener>

private val MAX_COUNT = 20

fun Listeners.all(): List<OsLocationListener> {
    synchronized(this) {
        val size = this.size
        val res = ArrayList<OsLocationListener>(size)
        for (i in 0 until size) {
            res.add(valueAt(i))
        }
        return res
    }
}

fun Listeners.update(client: Client, key: Any, listener: OsLocationListener) {
    synchronized(this) {
        try {
            listener.forwarder.prepareForRegistration()
        } catch (e: RemoteException) {
            logd{e}
            throw e.cause!!
        }
        if (size == 0) {
            val opMode = client.startMonitorAppOp(listener.provider)
            if (opMode != AppOpsManager.MODE_ALLOWED) {
                throw SecurityException("startMonitorAppOp returned " + opModeToString(opMode))
            }

        }
        client.locationManager.requestLocationUpdates(listener.provider.name, listener.request,
            GLocationService.listenerCallbacksExecutor, listener)

        val curIdx = indexOfKey(key)
        if (curIdx >= 0) {
            removeInternal(valueAt(curIdx))
            setValueAt(curIdx, listener)
        } else {
            put(key, listener)
            if (size > MAX_COUNT) {
                remove(client, key)
                throw IllegalStateException("too many ($size) listeners are already registered")
            }
        }
        logd{"client ${client.packageName} ${if (curIdx >= 0) "updated" else "added" } " +
                "listener $key, ${listener.request} provider ${listener.provider} listenerCount $size"}
    }
}

private fun removeInternal(listener: OsLocationListener) {
    try {
        listener.client.locationManager.removeUpdates(listener)
        listener.forwarder.callbackAfterUnregistration()
    } catch (e: Throwable) {
        Log.e("Listeners", "listener removal should never fail", e)
        System.exit(1)
    }
}

fun Listeners.remove(client: Client, key: Any) {
    var gc = false
    synchronized(this) {
        val idx = indexOfKey(key)
        if (idx < 0) {
            return
        }
        val listener = valueAt(idx)
        removeInternal(listener)
        removeAt(idx)
        logd{"client ${client.packageName} removed listener $key, listenerCount $size"}

        if (size == 0) {
            client.finishMonitorAppOp(listener.provider)
            gc = true
        }
    }
    if (gc) {
        // won't collect this instance, but may collect ones that were already empty
        GLocationService.gcMapOfListeners()
    }
}
