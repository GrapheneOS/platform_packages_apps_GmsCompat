package app.grapheneos.gmscompat.location

import android.Manifest
import android.app.AppOpsManager
import android.content.AttributionSource
import android.content.Context
import android.content.ContextParams
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.Criteria
import android.location.LocationManager
import android.os.Binder
import androidx.annotation.CheckResult
import com.google.android.gms.location.LocationRequest

class Client(val gls: GLocationService, unverifiedPackageName: String? = null, val attributionTag: String? = null, val appOpsReasonMessage: String? = null) {
    val uid = Binder.getCallingUid()
    val packageName: String
    val permission: Permission
    val ctx: Context

    init {
        val packageNames = gls.packageManager.getPackagesForUid(uid)!!

        packageName =
        if (unverifiedPackageName != null) {
            if (packageNames.contains(unverifiedPackageName)) {
                unverifiedPackageName
            } else {
                throw SecurityException("packageName $unverifiedPackageName doesn't belong to uid $uid")
            }
        } else {
            packageNames[0]
        }
        val glsCtx = gls.ctx
        permission =
        if (glsCtx.checkCallingPermission(Permission.FINE.string()) == PERMISSION_GRANTED) {
            Permission.FINE
        } else {
            glsCtx.enforceCallingPermission(Permission.COARSE.string(), null)
            Permission.COARSE
        }
        ctx = glsCtx
        /*
        LocationManager doesn't (yet?) use the new AttributionSource APIs.
        Once it does, there won't be a need to use AppOpsManager.

        val attrSource = AttributionSource.Builder(uid)
            .setAttributionTag(attributionTag)
            .setPackageName(packageName)
            .build()

        val cp = ContextParams.Builder()
            .setAttributionTag(packageName)
            .setNextAttributionSource(attrSource)
            .build()

        ctx = glsCtx.createContext(cp)
         */
    }

    val locationManager = ctx.getSystemService(LocationManager::class.java)!!

    fun getProvider(req: LocationRequest): String {
        val priority = req.priority
        if (priority == LocationRequest.PRIORITY_NO_POWER) {
            return LocationManager.PASSIVE_PROVIDER
        } else {
            return LocationManager.GPS_PROVIDER
        }
        // getBestProvider() returns FUSED_PROVIDER sometimes, which doesn't do anything useful when
        // there's no NETWORK_PROVIDER, but exposes more potential framework bugs
        /*
        val criteria = Criteria()
        when (priority) {
            LocationRequest.PRIORITY_HIGH_ACCURACY ->
                criteria.accuracy = Criteria.ACCURACY_FINE
            LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY ->
                criteria.accuracy = Criteria.ACCURACY_FINE
            LocationRequest.PRIORITY_LOW_POWER ->
                criteria.powerRequirement = Criteria.POWER_LOW
            else ->
                throw IllegalArgumentException(priority.toString())
        }
        return locationManager.getBestProvider(criteria, true)!!
         */
    }

    fun enforceFinePermission() {
        if (permission != Permission.FINE) {
            throw SecurityException("missing " + Permission.FINE.string())
        }
    }

    @CheckResult
    fun noteProxyAppOp(): Int {
        return gls.appOpsManager.noteProxyOpNoThrow(permission.appOp(), packageName, uid, attributionTag, appOpsReasonMessage)
    }

    @CheckResult
    fun startMonitorAppOp(): Int {
        return gls.appOpsManager.startProxyOpNoThrow(permission.monitorAppOp(), uid, packageName, attributionTag, appOpsReasonMessage)
    }

    fun finishMonitorAppOp() {
        gls.appOpsManager.finishProxyOp(permission.monitorAppOp(), uid, packageName, attributionTag)
    }
}

enum class Permission {
    COARSE, FINE;

    fun string(): String =
        when (this) {
            COARSE -> Manifest.permission.ACCESS_COARSE_LOCATION
            FINE -> Manifest.permission.ACCESS_FINE_LOCATION
        }

    fun appOp(): String =
        when (this) {
            COARSE -> AppOpsManager.OPSTR_COARSE_LOCATION
            FINE -> AppOpsManager.OPSTR_FINE_LOCATION
        }

    fun monitorAppOp(): String =
        when (this) {
            COARSE -> AppOpsManager.OPSTR_MONITOR_LOCATION
            FINE -> AppOpsManager.OPSTR_MONITOR_HIGH_POWER_LOCATION
        }
}
