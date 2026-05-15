package app.grapheneos.gmscompat.location

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.util.Log
import com.google.android.gms.location.ILocationCallback
import com.google.android.gms.location.ILocationListener
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationResult

abstract class GLocationForwarder {
    lateinit var listeners: Listeners

    abstract fun listenerKey(): Any

    abstract fun forwardLocations(ctx: Context, locations: List<Location>): Boolean
    abstract fun onLocationAvailabilityChanged(ctx: Context, la: LocationAvailability): Boolean

    fun unregister() {
        removeListener(listeners, listenerKey())
    }
}

class GlfPendingIntent(val pendingIntent: PendingIntent) : GLocationForwarder() {
    override fun forwardLocations(ctx: Context, locations: List<Location>): Boolean {
        val intent = Intent()
        intent.putExtra("com.google.android.gms.location.EXTRA_LOCATION_RESULT", LocationResult(locations))
//        intent.putExtra("com.google.android.location.LOCATION", locations.get(locations.size - 1))
        try {
            pendingIntent.send(ctx, 0, intent)
            return true
        } catch (e: PendingIntent.CanceledException) {
            Log.e("GlfPendingIntent", "", e)
            return false
        }
    }

    override fun onLocationAvailabilityChanged(ctx: Context, la: LocationAvailability): Boolean {
        val intent = Intent()
        intent.putExtra("com.google.android.gms.location.EXTRA_LOCATION_AVAILABILITY", la)
        try {
            pendingIntent.send(ctx, 0, intent)
            return true
        } catch (e: PendingIntent.CanceledException) {
            Log.e("GlfPendingIntent", "", e)
            return false
        }

    }

    override fun listenerKey(): Any = pendingIntent
}

abstract class GlfBinder(val binder: IBinder) : GLocationForwarder() {
    override fun listenerKey(): Any = binder
}

class GlfLocationCallback(val callback: ILocationCallback) : GlfBinder(callback.asBinder()) {
    override fun forwardLocations(ctx: Context, locations: List<Location>): Boolean {
        val lr = LocationResult(locations)
        // ILocationCallback is always in the same process, no need to handle DeadObjectException
        callback.onLocationResult(lr)
        return true
    }

    override fun onLocationAvailabilityChanged(ctx: Context, la: LocationAvailability): Boolean {
        // ILocationCallback is always in the same process, no need to handle DeadObjectException
        callback.onLocationAvailability(la)
        return true
    }
}

class GlfLocationListener(val listener: ILocationListener) : GlfBinder(listener.asBinder()) {
    override fun forwardLocations(ctx: Context, locations: List<Location>): Boolean {
        // same behavior as GmsCore
        locations.forEach {
            // ILocationListener is always in the same process, no need to handle DeadObjectException
            listener.onLocationChanged(it)
        }
        return true
    }

    override fun onLocationAvailabilityChanged(ctx: Context, la: LocationAvailability): Boolean {
        // ILocationListener doesn't have a corresponding callback
        return true
    }
}
