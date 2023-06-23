package app.grapheneos.gmscompat

import android.app.compat.gms.GmsCompat
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Bundle

import com.android.internal.gmscompat.GmsCompatApp

class RpcProvider : AbsContentProvider() {
    override fun call(whichStr: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = requireContext()
        return when (Integer.parseInt(whichStr)) {
            GmsCompatApp.RPC_GET_BINDER_IGms2Gca -> {
                // WRITE_GSERVICES is a signature-protected permission held by GSF, GMS Core and Play Store
                if (ctx.checkCallingPermission("com.google.android.providers.gsf.permission.WRITE_GSERVICES") != PackageManager.PERMISSION_GRANTED) {
                    val pkgName = ctx.packageManager.getPackagesForUid(Binder.getCallingUid())!![0]
                    if (!GmsCompat.isGmsApp(pkgName, ctx.userId)) {
                        throw SecurityException()
                    }
                }

                wrapBinder(BinderGms2Gca)
            }
            GmsCompatApp.RPC_GET_BINDER_IClientOfGmsCore2Gca -> {
                // any client of GMS Core is allowed to access this binder
                wrapBinder(BinderClientOfGmsCore2Gca)
            }
            else -> throw IllegalArgumentException(whichStr)
        }
    }

    private fun wrapBinder(binder: Binder): Bundle {
        val bundle = Bundle(1)
        bundle.putBinder(GmsCompatApp.KEY_BINDER, binder)
        return bundle
    }
}
