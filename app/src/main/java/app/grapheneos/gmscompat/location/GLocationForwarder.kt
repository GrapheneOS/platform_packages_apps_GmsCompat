package app.grapheneos.gmscompat.location

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.IBinder
import com.google.android.gms.location.ILocationCallback
import com.google.android.gms.location.ILocationListener
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationResult

abstract class GLocationForwarder {
    lateinit var osLocationListener: OsLocationListener
    lateinit var listeners: Listeners

    abstract fun listenerKey(): Any

    abstract fun forwardLocations(ctx: Context, locations: List<Location>)
    abstract fun onLocationAvailabilityChanged(ctx: Context, la: LocationAvailability)

    open fun prepareForRegistration() {}
    open fun callbackAfterUnregistration() {}

    fun unregister() {
        listeners.remove(osLocationListener.client, listenerKey())
    }
}

class GlfPendingIntent(val pendingIntent: PendingIntent) : GLocationForwarder() {
    override fun forwardLocations(ctx: Context, locations: List<Location>) {
        val intent = Intent()
        intent.putExtra("com.google.android.gms.location.EXTRA_LOCATION_RESULT", LocationResult(locations))
//        intent.putExtra("com.google.android.location.LOCATION", locations.get(locations.size - 1))
        pendingIntent.send(ctx, 0, intent)
    }

    override fun onLocationAvailabilityChanged(ctx: Context, la: LocationAvailability) {
        val intent = Intent()
        intent.putExtra("com.google.android.gms.location.EXTRA_LOCATION_AVAILABILITY", la)
        pendingIntent.send(ctx, 0, intent)
    }

    override fun listenerKey(): Any = pendingIntent
}

abstract class GlfBinder(val binder: IBinder) : GLocationForwarder(), IBinder.DeathRecipient {
    override fun prepareForRegistration() {
        binder.linkToDeath(this, 0)
    }

    override fun binderDied() {
        unregister()
    }

    override fun callbackAfterUnregistration() {
        binder.unlinkToDeath(this, 0)
    }

    override fun listenerKey(): Any = binder
}

class GlfLocationCallback(val callback: ILocationCallback) : GlfBinder(callback.asBinder()) {
    override fun forwardLocations(ctx: Context, locations: List<Location>) {
        val lr = LocationResult(locations)
        callback.onLocationResult(lr)
    }

    override fun onLocationAvailabilityChanged(ctx: Context, la: LocationAvailability) {
        callback.onLocationAvailability(la)
    }
}

class GlfLocationListener(val listener: ILocationListener) : GlfBinder(listener.asBinder()) {
    override fun forwardLocations(ctx: Context, locations: List<Location>) {
        // same behavior as GMS
        locations.forEach {
            listener.onLocationChanged(it)
        }
    }

    override fun onLocationAvailabilityChanged(ctx: Context, la: LocationAvailability) {
        // ILocationListener doesn't have a corresponding callback
    }
}
