package app.grapheneos.gmscompat.location

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.DeadObjectException
import android.os.SystemClock
import android.util.Log
import androidx.collection.IntObjectMap
import androidx.collection.MutableIntObjectMap
import app.grapheneos.gmscompat.Const
import app.grapheneos.gmscompat.logd
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationRequest
import java.util.Collections
import kotlin.math.max
import kotlin.math.min

fun LocationRequest.toOsLocationRequest(): android.location.LocationRequest {
    val interval =
    if (priority == LocationRequest.PRIORITY_NO_POWER) {
        android.location.LocationRequest.PASSIVE_INTERVAL
    } else {
        interval
    }
    val b = android.location.LocationRequest.Builder(interval)
    val quality = gmsPriorityToOsQuality(priority)
    b.setQuality(quality)
    b.setMinUpdateIntervalMillis(minUpdateIntervalMillis)
    b.setMaxUpdates(maxUpdates)
    b.setMinUpdateDistanceMeters(minUpdateDistanceMeters)
    b.setMaxUpdateDelayMillis(maxUpdateDelayMillis)

    if (expirationTime != Long.MAX_VALUE) {
        durationMillis = min(max(1L, expirationTime - SystemClock.elapsedRealtime()), durationMillis)
    }
    b.setDurationMillis(durationMillis)

    return b.build()
}

fun gmsPriorityToOsQuality(priority: Int): Int =
    when (priority) {
        LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY ->
            android.location.LocationRequest.QUALITY_BALANCED_POWER_ACCURACY
        LocationRequest.PRIORITY_HIGH_ACCURACY ->
            android.location.LocationRequest.QUALITY_HIGH_ACCURACY
        LocationRequest.PRIORITY_LOW_POWER ->
            android.location.LocationRequest.QUALITY_LOW_POWER
        LocationRequest.PRIORITY_NO_POWER ->
            android.location.LocationRequest.QUALITY_LOW_POWER
        else ->
            throw IllegalArgumentException()
    }

class OsLocationListener(val client: Client, val provider: OsLocationProvider,
                         val request: android.location.LocationRequest,
                         val forwarder: GLocationForwarder
) : LocationListener {
    companion object {
        const val TAG = "OsLocationListener"
    }

    override fun onLocationChanged(location: Location) {
        onLocationChanged(Collections.singletonList(location))
    }

    override fun onLocationChanged(locations: List<Location>) {
        locations.forEach {
            // mimic GMS location service
            it.provider = LocationManager.FUSED_PROVIDER
        }

        val locationsToForward = locations.map { provider.maybeFudge(it) }

        if (false) {
            // simulate movement
            locationsToForward.forEach {
                val off = (SystemClock.uptimeMillis() % 10_000) / 1_000_000.0
                it.latitude += off
                it.longitude += off
            }
        }
        if (!forwarder.forwardLocations(client.ctx, locationsToForward)) {
            Log.w(TAG, "forwardLocations failed, unregistering")
            forwarder.unregister()
        }
    }

    private fun onLocationAvailabilityChanged(available: Boolean) {
        if (!forwarder.onLocationAvailabilityChanged(client.ctx, LocationAvailability.get(available))) {
            Log.w(TAG, "onLocationAvailabilityChanged failed, unregistering")
            forwarder.unregister()
        }
    }

    override fun onProviderEnabled(provider: String) {
        logd{provider}
        check(provider == this.provider.name)
        onLocationAvailabilityChanged(true)
    }

    override fun onProviderDisabled(provider: String) {
        logd{provider}
        check(provider == this.provider.name)
        onLocationAvailabilityChanged(false)
    }

    private var isUnregistered = false

    fun unregister() {
        var flushCallbacks: MutableIntObjectMap<Runnable>? = null
        synchronized(this) {
            if (isUnregistered) {
                logd{"listener is already unregistered, skipping"};
                return
            }
            client.locationManager.removeUpdates(this)
            isUnregistered = true
            flushCallbacks = this.flushCallbacks
            this.flushCallbacks = null
        }
        // flush completion callbacks might not get delivered through onFlushComplete() after
        // removeUpdates()
        flushCallbacks?.forEachValue {
            it.run()
        }
    }

    private var flushCounter = 0
    private var flushCallbacks: MutableIntObjectMap<Runnable>? = null

    fun flush(onCompletion: Runnable) {
        var action: (() -> Unit)? = null
        synchronized(this) {
            if (isUnregistered) {
                action = {
                    logd{"listener is no longer registered, skipping"}
                    onCompletion.run()
                }
            } else {
                val listeners = flushCallbacks ?: MutableIntObjectMap<Runnable>().also { flushCallbacks = it }
                val flushId = flushCounter++
                check(!listeners.containsKey(flushId))
                listeners[flushId] = onCompletion
                action = {
                    client.locationManager.requestFlush(provider.name, this, flushId)
                }
            }
        }
        action!!.invoke()
    }

    override fun onFlushComplete(requestCode: Int) {
        var callback: Runnable? = null
        synchronized(this) {
            callback = flushCallbacks?.remove(requestCode)
        }
        if (callback == null) {
            logd{"no completion callback"}
        } else {
            callback.run()
        }
    }
}
