package app.grapheneos.gmscompat.location

import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import com.google.android.gms.location.LocationRequest

class OsLocationProvider(val name: String, val properties: ProviderProperties?, val fudger: LocationFudger?, val permission: Permission) {

    fun maybeFudge(location: Location): Location {
        if (fudger == null) {
            return location
        }

        return fudger.createCoarse(location)
    }

    companion object {
        fun get(client: Client, req: LocationRequest) = get(client, req.priority, req.granularity)

        fun get(client: Client, priority: Int, granularity: Int): OsLocationProvider {
            val name = if (priority == LocationRequest.PRIORITY_NO_POWER) {
                LocationManager.PASSIVE_PROVIDER
            } else {
                LocationManager.GPS_PROVIDER
            }
            return get(client, name, granularity)
        }

        fun get(client: Client, granularity: Int): OsLocationProvider {
            val name = LocationManager.GPS_PROVIDER
            return get(client, name, granularity)
        }

        fun get(client: Client, name: String, granularity: Int): OsLocationProvider {
            val properties: ProviderProperties? = client.locationManager.getProviderProperties(name)

            var permission = client.permission

            val fudger: LocationFudger? = when (client.permission) {
                Permission.COARSE -> {
                    when (granularity) {
                        LocationRequest.GRANULARITY_COARSE,
                        LocationRequest.GRANULARITY_PERMISSION_LEVEL, -> null
                        LocationRequest.GRANULARITY_FINE -> throw SecurityException()
                        else -> throw IllegalArgumentException()
                    }
                }
                Permission.FINE -> {
                    when (granularity) {
                        LocationRequest.GRANULARITY_COARSE -> {
                            permission = Permission.COARSE
                            if (properties == null) {
                                LocationFudger()
                            } else when (properties.accuracy) {
                                ProviderProperties.ACCURACY_FINE -> LocationFudger()
                                ProviderProperties.ACCURACY_COARSE -> null
                                else -> throw IllegalStateException()
                            }
                        }
                        LocationRequest.GRANULARITY_PERMISSION_LEVEL,
                        LocationRequest.GRANULARITY_FINE ->
                            null
                        else -> throw IllegalArgumentException()
                    }
                }
            }

            return OsLocationProvider(name, properties, fudger, permission)
        }
    }
}
