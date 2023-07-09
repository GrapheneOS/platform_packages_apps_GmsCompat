package app.grapheneos.gmscompat.location

import android.Manifest
import android.content.Context
import android.content.ContextParams
import android.content.pm.PackageManager
import android.location.LocationManager

class Client(val gls: GLocationService, pkgName: String? = null, val attributionTag: String? = null) {
    val packageName: String
    val permission: Permission
    val ctx: Context

    init {
        val glsCtx = gls.ctx

        packageName = pkgName ?: glsCtx.packageName

        permission = if (Permission.FINE.isGranted(glsCtx)) {
            Permission.FINE
        } else {
            if (!Permission.COARSE.isGranted(glsCtx)) {
                throw SecurityException("no location permission")
            }
            Permission.COARSE
        }

        ctx = if (attributionTag != null) {
            val cp = ContextParams.Builder().run {
                setAttributionTag(attributionTag)
                build()
            }
            glsCtx.createContext(cp)
        } else {
            glsCtx
        }
    }

    val locationManager = ctx.getSystemService(LocationManager::class.java)!!
}

enum class Permission(val string: String) {
    COARSE(Manifest.permission.ACCESS_COARSE_LOCATION),
    FINE(Manifest.permission.ACCESS_FINE_LOCATION),
    ;

    fun isGranted(ctx: Context): Boolean {
        return ctx.checkSelfPermission(string) == PackageManager.PERMISSION_GRANTED
    }
}
