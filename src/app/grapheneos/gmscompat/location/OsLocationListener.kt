package app.grapheneos.gmscompat.location

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.SystemClock
import app.grapheneos.gmscompat.Const
import app.grapheneos.gmscompat.logd
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationRequest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
        var unregister = true
        try {
            forwarder.forwardLocations(client.ctx, locationsToForward)
            unregister = false
        } catch (e: Exception) {
            logd{e}
        }
        if (unregister) {
            forwarder.unregister()
        }
    }

    private fun onLocationAvailabilityChanged(available: Boolean) {
        try {
            forwarder.onLocationAvailabilityChanged(client.ctx, LocationAvailability.get(available))
        } catch (e: Exception) {
            logd{e}
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

    private val flushLock = Any()
    private var nextFlushRequestCode = 0
    @Volatile
    private var activeFlushRequestCode = 0
    @Volatile
    private var flushLatch: CountDownLatch? = null

    fun flush() {
        // flushLock serializes flushes on the same listener; the request code lets
        // onFlushComplete() ignore late callbacks from a previous timed-out flush
        synchronized(flushLock) {
            val l = CountDownLatch(1)
            val code = ++nextFlushRequestCode
            // publish before requestFlush() so a fast callback sees them
            flushLatch = l
            activeFlushRequestCode = code
            try {
                client.locationManager.requestFlush(provider.name, this, code)
            } catch (e: IllegalStateException) {
                // may get thrown if other thread unregisters this listener
                flushLatch = null
                return
            }
            try {
                // bounded wait: a flush callback is not guaranteed to arrive if this listener
                // gets unregistered concurrently
                if (!l.await(5, TimeUnit.SECONDS)) {
                    logd{"flush timed out"}
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                flushLatch = null
            }
        }
    }

    override fun onFlushComplete(requestCode: Int) {
        if (requestCode == activeFlushRequestCode) {
            flushLatch?.countDown()
        }
    }
}
