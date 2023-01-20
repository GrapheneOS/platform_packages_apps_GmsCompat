package app.grapheneos.gmscompat.location

import android.app.AppOpsManager
import android.app.AppOpsManager.MODE_ALLOWED
import android.location.GnssAntennaInfo
import android.location.GnssMeasurementsEvent
import android.location.GnssNavigationMessage
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Binder
import android.os.CancellationSignal
import android.os.RemoteException
import android.util.SparseArray
import com.google.android.gms.common.api.Status
import com.google.android.gms.common.api.internal.IStatusCallback
import com.google.android.gms.common.internal.ICancelToken
import com.google.android.gms.location.ILocationCallback
import com.google.android.gms.location.ILocationListener
import com.google.android.gms.location.LocationAvailability
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
import com.google.android.gms.location.internal.LocationRequestUpdateData
import app.grapheneos.gmscompat.App
import app.grapheneos.gmscompat.Const
import app.grapheneos.gmscompat.logd
import app.grapheneos.gmscompat.objectToString
import app.grapheneos.gmscompat.opModeToString
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LastLocationRequest
import com.google.android.gms.location.LocationAvailabilityRequest
import com.google.android.gms.location.internal.LocationReceiver
import java.lang.ref.WeakReference
import java.util.Arrays
import java.util.concurrent.Executors
import java.util.function.Consumer

object GLocationService : IGoogleLocationManagerService.Stub() {
    val ctx = App.ctx()
    val appOpsManager = ctx.getSystemService(AppOpsManager::class.java)!!
    val packageManager = ctx.packageManager!!
    val listenerCallbacksExecutor = Executors.newCachedThreadPool()
    val nonClientLocationManager = ctx.getSystemService(LocationManager::class.java)!!

    // maps app uid to its registered listeners
    // gc would be hard to do correctly if listeners were strongly referenced
    val mapOfListeners = SparseArray<WeakReference<Listeners>>(50)

    override fun registerLocationReceiver(receiver: LocationReceiver, request: LocationRequest, callback: IStatusCallback) {
        val client = Client(this)

//        logd{client.packageName}

        val clientListeners = getOrCreateListeners(client)

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
        glf.listeners = clientListeners

        val provider = OsLocationProvider.get(client, request)

        val oll = OsLocationListener(client, provider, request.toOsLocationRequest(), glf)
        clientListeners.update(client, key, oll)

        callback.onCompletion(Status.SUCCESS)
    }

    override fun unregisterLocationReceiver(receiver: LocationReceiver, callback: IStatusCallback) {
        val client = Client(this)

        val clientListeners = getOrCreateListeners(client)

        val key: Any = when (receiver.type) {
            LocationReceiver.TYPE_ILocationListener,
            LocationReceiver.TYPE_ILocationCallback ->
                receiver.binder
            LocationReceiver.TYPE_PendingIntent ->
                receiver.pendingIntent
            else -> throw IllegalArgumentException()
        }

//        logd{"${client.packageName} key ${key}"}

        clientListeners.remove(client, key)
        callback.onCompletion(Status.SUCCESS)
    }

    private fun getOrCreateListeners(client: Client): Listeners {
        return synchronized(mapOfListeners) {
            val cur = mapOfListeners.get(client.uid)?.get()
            if (cur != null) {
                cur
            } else {
                val l = Listeners(7)
                mapOfListeners.put(client.uid, WeakReference(l))
                l
            }
        }
    }

    override fun updateLocationRequest(data: LocationRequestUpdateData) {
//        logd{data}
        val pendingIntent = data.pendingIntent
        val client = try {
            Client(this, pendingIntent?.creatorPackage, data.request?.contextAttributionTag, data.appOpsReasonMessage)
        } catch (e: SecurityException) {
            data.fusedLocationProviderCallback?.onFusedLocationProviderResult(
                FusedLocationProviderResult(Status(CommonStatusCodes.INTERNAL_ERROR)))
            return
        }

        val clientListeners = getOrCreateListeners(client)

        if (data.opCode == LocationRequestUpdateData.OP_REQUEST_UPDATES) {
            val key: Any
            val glf: GLocationForwarder
            if (pendingIntent != null) {
                if (pendingIntent.creatorUid != client.uid) {
                    throw SecurityException()
                }
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
            glf.listeners = clientListeners

            val req: LocationRequest = data.request.request
            val oll = OsLocationListener(client, OsLocationProvider.get(client, req),
                    req.toOsLocationRequest(), glf)
            clientListeners.update(client, key, oll)
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
            clientListeners.remove(client, key)
        }
        data.fusedLocationProviderCallback?.onFusedLocationProviderResult(FusedLocationProviderResult.SUCCESS)
    }

    fun gcMapOfListeners() {
        val map: SparseArray<WeakReference<Listeners>> = mapOfListeners
        synchronized(map) {
            var i = 0
            while (i < map.size()) {
                if (map.valueAt(i).get() == null) {
                    map.removeAt(i)
                } else {
                    ++i
                }
            }
        }
    }

    // https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient#flushLocations()
    override fun flushLocations(callback: IFusedLocationProviderCallback) {
        val client = Client(this)
        logd{"client ${client.packageName}"}

        val listeners =
        synchronized(mapOfListeners) {
            val cur = mapOfListeners.get(client.uid)?.get()
            if (cur != null) {
                cur
            } else {
                return
            }
        }
        listeners.all().forEach {
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
        logd{"client ${client.packageName} contextAttributionTag $contextAttributionTag"}

        val opMode = client.noteProxyAppOp(client.permission)
        if (opMode != MODE_ALLOWED) {
            if (Const.DEV) {
                throw SecurityException("noteProxyAppOp returned ${opModeToString(opMode)}")
            }
            return null
        }

        val provider = OsLocationProvider.get(client, LocationRequest.GRANULARITY_PERMISSION_LEVEL)

        return client.locationManager.getLastKnownLocation(provider.name)?.let {
            provider.maybeFudge(it)
        }
    }

    override fun getLastLocation4(request: LastLocationRequest, callback: ILocationStatusCallback) {
        val client = Client(this)
        logd{"client ${client.packageName} maxAge ${request.maxAge}"}

        val provider = OsLocationProvider.get(client, request.granularity)

        var location = client.locationManager.getLastKnownLocation(provider.name)?.let {
            if (it.elapsedRealtimeAgeMillis <= request.maxAge) {
                provider.maybeFudge(it)
            } else {
                null
            }
        }

        if (location != null) {
            val opMode = client.noteProxyAppOp(provider)
            if (opMode != MODE_ALLOWED) {
                logd{"opMode ${opModeToString(opMode)}"}
                location = null
                // GmsCore returns Status.SUCCESS even in this case
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
        // GMS ignores packageName too
        return getLastLocation3(null)
    }

    override fun getCurrentLocation(request: CurrentLocationRequest, callback: ILocationStatusCallback): ICancelToken {
        val client = Client(this)

        val earlyRejectCheck = client.earlyRejectCheck(client.permission)
        if (earlyRejectCheck != MODE_ALLOWED) {
            logd{client.packageName + " earlyRejectCheck returned ${opModeToString(earlyRejectCheck)}"}
            // GmsCore returns Status.SUCCESS too
            callback.onResult(Status.SUCCESS, null)
            return object : ICancelToken.Stub() {
                override fun cancel() {}
            }
        }

        logd{client.packageName}

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
                var location: Location? = provider.maybeFudge(origLocation)

                val opMode = client.noteProxyAppOp(provider)
                if (opMode != MODE_ALLOWED) {
                    logd{"${client.packageName} opMode ${opModeToString(opMode)}"}
                    // GmsCore returns Status.SUCCESS even in this case
                    location = null
                }

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
                logd{"cancel signal from ${client.packageName}"}
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
        logd{"client ${client.packageName}"}
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
        logd{"client: ${packageManager.getPackagesForUid(Binder.getCallingUid())?.first()} ${objectToString(settingsRequest)}"}
        // GMS doesn't check whether caller has a location permission in this case

        val lss = LocationSettingsStates()
        lss.gpsPresent = true
        lss.gpsUsable = nonClientLocationManager.isLocationEnabled()
//        lss.networkLocationPresent = true
//        lss.networkLocationUsable = lss.gpsUsable

        callback.onLocationSettingsResult(LocationSettingsResult(lss, Status.SUCCESS))
    }

    @JvmField
    val CODES = intArrayOf(
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
    init {
        Arrays.sort(CODES)
    }

    init {
        if (Const.DEV
            && false
        ) {
            val lm = nonClientLocationManager
            val executor = listenerCallbacksExecutor
            lm.addNmeaListener(executor, object : OnNmeaMessageListener {
                override fun onNmeaMessage(message: String?, timestamp: Long) {
    //                logd{"msg " + message + " timestamp " + timestamp}
                }
            })
            lm.registerAntennaInfoListener(executor, object : GnssAntennaInfo.Listener {
                override fun onGnssAntennaInfoReceived(gnssAntennaInfos: MutableList<GnssAntennaInfo>) {
                    logd{Arrays.toString(gnssAntennaInfos.toTypedArray())}
                }
            })
            lm.registerGnssMeasurementsCallback(executor, object : GnssMeasurementsEvent.Callback() {
                override fun onGnssMeasurementsReceived(eventArgs: GnssMeasurementsEvent) {
    //                logd{"measurements ${eventArgs.measurements} clock ${eventArgs.clock}"}
                }
            })
            lm.registerGnssNavigationMessageCallback(executor, object : GnssNavigationMessage.Callback() {
                override fun onGnssNavigationMessageReceived(event: GnssNavigationMessage) {
                    logd{event.toString()}
                }
            })
            lm.registerGnssStatusCallback(executor, object : GnssStatus.Callback() {
                override fun onStarted() {
                    logd()
                }

                override fun onStopped() {
                    logd()
                }

                override fun onFirstFix(ttffMillis: Int) {
                    logd{ttffMillis}
                }

                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    logd{"satellite count ${status.satelliteCount}"}
                }
            })
        }
    }
}
