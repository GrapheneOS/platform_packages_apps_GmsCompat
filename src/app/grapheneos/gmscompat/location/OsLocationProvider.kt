package app.grapheneos.gmscompat.location

import android.location.LastLocationRequest
import android.location.Location
import android.location.LocationManager
import com.google.android.gms.location.LocationRequest

class OsLocationProvider(val name: String, val coarse: Boolean) {

    fun getLastLocation(client: Client): Location? {
        val request = LastLocationRequest.Builder().run {
            setCoarse(coarse)
            build()
        }
        return client.locationManager.getLastKnownLocation(name, request)
    }

    companion object {
        fun get(client: Client, req: LocationRequest) = get(client, req.priority, req.granularity)

        fun get(client: Client, priority: Int, granularity: Int): OsLocationProvider {
            val name = if (priority == LocationRequest.PRIORITY_NO_POWER) {
                LocationManager.PASSIVE_PROVIDER
            } else if (priority == LocationRequest.PRIORITY_LOW_POWER) {
                if (client.locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    // GmsCore location service doesn't use GNSS for low-power requests
                    LocationManager.NETWORK_PROVIDER
                } else {
                    // fused provider doesn't use GNSS for low-power requests, i.e. it doesn't
                    // provide any locations for low-power requests when network location is off
                    LocationManager.GPS_PROVIDER
                }
            } else {
                LocationManager.FUSED_PROVIDER
            }
            return get(client, name, granularity)
        }

        fun getPassive(client: Client, granularity: Int): OsLocationProvider {
            return get(client, LocationManager.PASSIVE_PROVIDER, granularity)
        }

        fun get(client: Client, name: String, granularity: Int): OsLocationProvider {
            val coarse = when (client.permission) {
                Permission.COARSE -> {
                    when (granularity) {
                        LocationRequest.GRANULARITY_COARSE,
                        LocationRequest.GRANULARITY_PERMISSION_LEVEL, -> false
                        LocationRequest.GRANULARITY_FINE -> throw SecurityException()
                        else -> throw IllegalArgumentException()
                    }
                }
                Permission.FINE -> {
                    when (granularity) {
                        LocationRequest.GRANULARITY_COARSE -> true
                        LocationRequest.GRANULARITY_PERMISSION_LEVEL,
                        LocationRequest.GRANULARITY_FINE -> false
                        else -> throw IllegalArgumentException()
                    }
                }
            }

            return OsLocationProvider(name, coarse)
        }
    }
}
