package app.grapheneos.gmscompat.location

import android.app.AppOpsManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.SystemClock
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationRequest

import app.grapheneos.gmscompat.Const
import app.grapheneos.gmscompat.logd
import app.grapheneos.gmscompat.opModeToString
import java.lang.IllegalStateException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import kotlin.math.max

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
    if (explicitFastestInterval) {
        b.setMinUpdateIntervalMillis(minUpdateIntervalMillis)
    }
    b.setDurationMillis(max(1, expirationTime - SystemClock.elapsedRealtime()))
    b.setMaxUpdates(maxUpdates)
    b.setMinUpdateDistanceMeters(minUpdateDistanceMeters)
    b.setMaxUpdateDelayMillis(maxUpdateDelayMillis)
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

class OsLocationListener(val client: Client, val provider: String,
                         val request: android.location.LocationRequest,
                         val forwarder: GLocationForwarder
) : LocationListener {
    init {
        forwarder.osLocationListener = this
    }

    private val fudger =
        if (client.permission == Permission.COARSE) {
            LocationFudger()
        } else {
            null
        }

    override fun onLocationChanged(location: Location) {
        onLocationChanged(Collections.singletonList(location))
    }

    override fun onLocationChanged(locations: List<Location>) {
        val opMode = client.noteProxyAppOp()
        if (opMode != AppOpsManager.MODE_ALLOWED) {
            logd{"noteProxyAppOp returned ${opModeToString(opMode)}, unregister " + forwarder.listenerKey()}
            forwarder.unregister()
            return
        }

        locations.forEach {
            it.provider = LocationManager.FUSED_PROVIDER
        }

        val locationsToForward =
        if (fudger != null) {
            locations.map {
                fudger.createCoarse(it)
            }
        } else {
            locations
        }

        if (Const.DEV
            && false
        ) {
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
        check(provider == this.provider)
        onLocationAvailabilityChanged(true)
    }

    override fun onProviderDisabled(provider: String) {
        logd{provider}
        check(provider == this.provider)
        onLocationAvailabilityChanged(false)
    }

    private var flushLatch: CountDownLatch? = null

    fun flush() {
        val l = CountDownLatch(1)
        synchronized(this) {
            flushLatch = l
            try {
                client.locationManager.requestFlush(provider, this, 0)
            } catch (e: IllegalStateException) {
                // may get thrown if other thread unregisters this listener
                return
            }
            l.await()
            flushLatch = null
        }
    }

    override fun onFlushComplete(requestCode: Int) {
        flushLatch!!.countDown()
    }
}
