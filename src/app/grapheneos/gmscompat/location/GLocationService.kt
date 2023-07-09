package app.grapheneos.gmscompat.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.RemoteException
import androidx.annotation.Keep
import app.grapheneos.gmscompat.BinderDefSupplier
import app.grapheneos.gmscompat.logd
import app.grapheneos.gmscompat.objectToString
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.android.gms.common.api.internal.IStatusCallback
import com.google.android.gms.common.internal.ICancelToken
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.ILocationCallback
import com.google.android.gms.location.ILocationListener
import com.google.android.gms.location.LastLocationRequest
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationAvailabilityRequest
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResult
import com.google.android.gms.location.LocationSettingsStates
import com.google.android.gms.location.internal.FusedLocationProviderResult
import com.google.android.gms.location.internal.IFusedLocationProviderCallback
import com.google.android.gms.location.internal.IGoogleLocationManagerService
import com.google.android.gms.location.internal.ILocationAvailabilityStatusCallback
import com.google.android.gms.location.internal.ILocationStatusCallback
import com.google.android.gms.location.internal.ISettingsCallbacks
import com.google.android.gms.location.internal.LocationReceiver
import com.google.android.gms.location.internal.LocationRequestUpdateData
import java.util.concurrent.Executors
import java.util.function.Consumer

@Keep
class GLocationService(val ctx: Context) : IGoogleLocationManagerService.Stub() {
    val listenerCallbacksExecutor = Executors.newCachedThreadPool()
    val nonClientLocationManager = ctx.getSystemService(LocationManager::class.java)!!

    val listeners = Listeners(7)

    override fun registerLocationReceiver(receiver: LocationReceiver, request: LocationRequest, callback: IStatusCallback) {
        val client = Client(this)

        val key: Any
        val glf: GLocationForwarder
        when (receiver.type) {
            LocationReceiver.TYPE_ILocationListener -> {
                key = receiver.binder
                glf = GlfLocationListener(ILocationListener.Stub.asInterface(receiver.binder))
            }
            LocationReceiver.TYPE_ILocationCallback -> {
                key = receiver.binder
                glf = GlfLocationCallback(ILocationCallback.Stub.asInterface(receiver.binder))
            }
            LocationReceiver.TYPE_PendingIntent -> {
                key = receiver.pendingIntent
                glf = GlfPendingIntent(receiver.pendingIntent)
            }
            else -> {
                throw IllegalArgumentException()
            }
        }
        glf.listeners = listeners

        val provider = OsLocationProvider.get(client, request)

        val oll = OsLocationListener(client, provider, request.toOsLocationRequest(), glf)
        updateListener(this, client, key, oll)

        callback.onCompletion(Status.SUCCESS)
    }

    override fun unregisterLocationReceiver(receiver: LocationReceiver, callback: IStatusCallback) {
        Client(this)

        val key: Any = when (receiver.type) {
            LocationReceiver.TYPE_ILocationListener,
            LocationReceiver.TYPE_ILocationCallback ->
                receiver.binder
            LocationReceiver.TYPE_PendingIntent ->
                receiver.pendingIntent
            else -> throw IllegalArgumentException()
        }
//        logd{"key ${key}"}

        removeListener(listeners, key)
        callback.onCompletion(Status.SUCCESS)
    }

    override fun updateLocationRequest(data: LocationRequestUpdateData) {
//        logd{data}
        val pendingIntent = data.pendingIntent
        val client = try {
            Client(this, pendingIntent?.creatorPackage, data.request?.contextAttributionTag)
        } catch (e: SecurityException) {
            data.fusedLocationProviderCallback?.onFusedLocationProviderResult(
                FusedLocationProviderResult(Status(CommonStatusCodes.INTERNAL_ERROR)))
            return
        }

        if (data.opCode == LocationRequestUpdateData.OP_REQUEST_UPDATES) {
            val key: Any
            val glf: GLocationForwarder
            if (pendingIntent != null) {
                key = pendingIntent
                glf = GlfPendingIntent(pendingIntent)
            } else {
                val lcallback: ILocationCallback? = data.callback
                val llistener: ILocationListener? = data.listener
                if (lcallback != null) {
                    key = lcallback.asBinder()
                    glf = GlfLocationCallback(lcallback)
                } else {
                    key = llistener!!.asBinder()
                    glf = GlfLocationListener(llistener)
                }
            }
            glf.listeners = listeners

            val req: LocationRequest = data.request.request
            val oll = OsLocationListener(client, OsLocationProvider.get(client, req),
                    req.toOsLocationRequest(), glf)
            updateListener(this, client, key, oll)
        } else {
            require(data.opCode == LocationRequestUpdateData.OP_REMOVE_UPDATES)
            val key: Any
            if (pendingIntent != null) {
                key = pendingIntent
            } else {
                val lcallback: ILocationCallback? = data.callback
                val llistener: ILocationListener? = data.listener
                if (lcallback != null) {
                    key = lcallback.asBinder()
                } else {
                    key = llistener!!.asBinder()
                }
            }
            removeListener(listeners, key)
        }
        data.fusedLocationProviderCallback?.onFusedLocationProviderResult(FusedLocationProviderResult.SUCCESS)
    }

    // https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient#flushLocations()
    override fun flushLocations(callback: IFusedLocationProviderCallback) {
        Client(this)
        logd{}

        getAllOsLocationListeners(listeners).forEach {
            it.flush()
        }
        callback.onFusedLocationProviderResult(FusedLocationProviderResult.SUCCESS)
    }

    // https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient#getLastLocation()
    override fun getLastLocation3(contextAttributionTag: String?): Location? {
        val client = try {
            Client(this, attributionTag = contextAttributionTag)
        } catch (e: SecurityException) {
            return null
        }
        logd{"contextAttributionTag $contextAttributionTag"}

        val provider = OsLocationProvider.get(client, LocationRequest.GRANULARITY_PERMISSION_LEVEL)

        return client.locationManager.getLastKnownLocation(provider.name)?.let {
            provider.maybeFudge(it)
        }
    }

    override fun getLastLocation4(request: LastLocationRequest, callback: ILocationStatusCallback) {
        val client = Client(this)
        logd{"maxAge ${request.maxAge}"}

        val provider = OsLocationProvider.get(client, request.granularity)

        val location = client.locationManager.getLastKnownLocation(provider.name)?.let {
            if (it.elapsedRealtimeAgeMillis <= request.maxAge) {
                provider.maybeFudge(it)
            } else {
                null
            }
        }

        callback.onResult(Status.SUCCESS, location)
    }

    override fun getLastLocation5(request: LastLocationRequest, receiver: LocationReceiver) {
        require(receiver.type == LocationReceiver.TYPE_ILocationStatusCallback)
        getLastLocation4(request, ILocationStatusCallback.Stub.asInterface(receiver.binder))
    }

    override fun getLastLocation(): Location? {
        logd()
        return getLastLocation3(null)
    }

    override fun getLastLocation2(packageName: String?): Location? {
        logd()
        // GmsCore ignores packageName too
        return getLastLocation3(null)
    }

    override fun getCurrentLocation(request: CurrentLocationRequest, callback: ILocationStatusCallback): ICancelToken {
        val client = Client(this)

        logd{}

        val osRequest = android.location.LocationRequest.Builder(0L).apply {
            setDurationMillis(request.durationMillis)
            setQuality(gmsPriorityToOsQuality(request.priority))
        }.build()

        val provider = OsLocationProvider.get(client, request.priority, request.granularity)

        val consumer = Consumer<Location> { origLocation ->
            // location.elapsedRealtimeAgeMillis <= request.maxUpdateAgeMillis
            // check is not needed, maxUpdateAgeMillis applies only to historical locations
            // which are never returned by OsLocationProvider
            if (origLocation != null) {
                val location: Location = provider.maybeFudge(origLocation)

                try {
                    callback.onResult(Status.SUCCESS, location)
                } catch (e: RemoteException) {
                    logd{e}
                }
            } else {
                try {
                    callback.onResult(Status(CommonStatusCodes.TIMEOUT), null)
                } catch (e: RemoteException) {
                    logd{e}
                }
            }
        }

        val cancellationSignal = CancellationSignal()

        client.locationManager.getCurrentLocation(provider.name, osRequest, cancellationSignal,
            listenerCallbacksExecutor, consumer)

        return object : ICancelToken.Stub() {
            override fun cancel() {
                logd{"cancel signal"}
                cancellationSignal.cancel()
            }
        }
    }

    override fun getCurrentLocation2(request: CurrentLocationRequest, receiver: LocationReceiver): ICancelToken {
        require(receiver.type == LocationReceiver.TYPE_ILocationStatusCallback)
        return getCurrentLocation(request, ILocationStatusCallback.Stub.asInterface(receiver.binder))
    }

    // https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient#getLocationAvailability()
    override fun getLocationAvailability(packageName: String?): LocationAvailability {
        val client = try {
            Client(this)
        } catch (e: SecurityException) {
            return LocationAvailability.get(false)
        }
        return LocationAvailability.get(client.locationManager.isLocationEnabled())
    }

    override fun getLocationAvailability2(request: LocationAvailabilityRequest, receiver: LocationReceiver) {
        val res = getLocationAvailability(null)

        require(receiver.type == LocationReceiver.TYPE_ILocationAvailabilityStatusCallback)
        val callback = ILocationAvailabilityStatusCallback.Stub.asInterface(receiver.binder)
        callback.onResult(Status.SUCCESS, res)
    }

    // https://developers.google.com/android/reference/com/google/android/gms/location/SettingsClient#checkLocationSettings(com.google.android.gms.location.LocationSettingsRequest)
    override fun requestLocationSettingsDialog(
        settingsRequest: LocationSettingsRequest,
        callback: ISettingsCallbacks,
        packageName: String?
    ) {
        logd{objectToString(settingsRequest)}
        // GmsCore doesn't check whether caller has a location permission in this case

        val lss = LocationSettingsStates()
        lss.gpsPresent = true
        lss.gpsUsable = nonClientLocationManager.isLocationEnabled()
//        lss.networkLocationPresent = true
//        lss.networkLocationUsable = lss.gpsUsable

        callback.onLocationSettingsResult(LocationSettingsResult(lss, Status.SUCCESS))
    }

    class BinderDef : BinderDefSupplier(IGoogleLocationManagerService.DESCRIPTOR, GLocationService::class) {

        override fun transactionCodes(callerPkg: String) = intArrayOf(
            // to generate, copy TRANSACTION_* definitions from IGoogleLocationManagerService.Stub and run:
            // xsel --clipboard | sed -e 's/static final int TRANSACTION_/\/* /' -e 's/=/*\//' -e 's/;/,/' | xsel --clipboard --input

            /* getLastLocation */ 7,
            /* getLastLocation2 */ 21,
            /* getLastLocation3 */ 80,
            /* getLastLocation4 */ 82,
            /* getLastLocation5 */ 90,
            /* getLocationAvailability */ 34,
            /* getLocationAvailability2 */ 91,
            /* requestLocationSettingsDialog */ 63,
            /* updateLocationRequest */ 59,
            /* flushLocations */ 67,
            /* registerLocationReceiver */ 88,
            /* unregisterLocationReceiver */ 89,
            /* getCurrentLocation */ 87,
            /* getCurrentLocation2 */ 92,
        )
    }
}
