package app.grapheneos.gmscompat.location

import android.util.ArrayMap
import android.util.Log
import app.grapheneos.gmscompat.logd

// all registered listeners. key is either Binder or PendingIntent
typealias Listeners = ArrayMap<Any, OsLocationListener>

private val MAX_COUNT = 100

fun getAllOsLocationListeners(listeners: Listeners): List<OsLocationListener> {
    synchronized(listeners) {
        val size = listeners.size
        val res = ArrayList<OsLocationListener>(size)
        for (i in 0 until size) {
            res.add(listeners.valueAt(i))
        }
        return res
    }
}

fun updateListener(gls: GLocationService, client: Client, key: Any, listener: OsLocationListener) {
    val listeners = gls.listeners
    synchronized(listeners) {
        client.locationManager.requestLocationUpdates(listener.provider.name, listener.request,
            gls.listenerCallbacksExecutor, listener)

        val curIdx = listeners.indexOfKey(key)
        if (curIdx >= 0) {
            removeInternal(listeners.valueAt(curIdx))
            listeners.setValueAt(curIdx, listener)
        } else {
            listeners.put(key, listener)
            if (listeners.size > MAX_COUNT) {
                removeListener(listeners, key)
                throw IllegalStateException("too many (${listeners.size}) listeners are already registered")
            }
        }
        logd{"client ${client.packageName} ${if (curIdx >= 0) "updated" else "added" } " +
                "listener ${key.javaClass}, ${listener.request} provider ${listener.provider} listenerCount ${listeners.size}"}
    }
}

private fun removeInternal(listener: OsLocationListener) {
    try {
        listener.client.locationManager.removeUpdates(listener)
    } catch (e: Throwable) {
        Log.e("Listeners", "listener removal should never fail", e)
        System.exit(1)
    }
}

fun removeListener(listeners: Listeners, key: Any) {
    synchronized(listeners) {
        val idx = listeners.indexOfKey(key)
        if (idx < 0) {
            return
        }
        val listener = listeners.valueAt(idx)
        removeInternal(listener)
        listeners.removeAt(idx)
        logd{"removed listener ${key.javaClass}, listenerCount ${listeners.size}"}
    }
}
