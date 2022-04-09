package app.grapheneos.gmscompat

import android.os.Binder
import android.os.Bundle

import com.android.internal.gmscompat.GmsCompatApp

class BinderProvider : AbsContentProvider() {
    override fun call(whichStr: String, arg: String?, extras: Bundle?): Bundle? {
        val binder: Binder = when (Integer.parseInt(whichStr)) {
            GmsCompatApp.BINDER_IGms2Gca -> {
                // WRITE_GSERVICES is a signature-protected permission held by GSF, GMS Core and Play Store
                context.enforceCallingPermission("com.google.android.providers.gsf.permission.WRITE_GSERVICES", null)
                BinderGms2Gca
            }
            GmsCompatApp.BINDER_IClientOfGmsCore2Gca -> {
                // any client of GMS Core is allowed to access this binder
                BinderClientOfGmsCore2Gca
            }
            else -> throw IllegalArgumentException(whichStr)
        }
        val bundle = Bundle(1)
        bundle.putBinder(GmsCompatApp.KEY_BINDER, binder)
        return bundle
    }
}
