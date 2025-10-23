package app.grapheneos.gmscompat.lib.sysservice.client;

import android.app.AppGlobals;
import android.app.ContentProviderHolder;
import android.app.IActivityManager;
import android.app.IApplicationThread;
import android.content.ComponentName;
import android.content.Intent;
import android.ext.PackageId;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

class GclActivityManager extends IActivityManager.Stub.Proxy {
    static final String TAG = "GclActivityManager";

    GclActivityManager(IBinder remote) { super(remote); }

    @Override
    public ContentProviderHolder getContentProvider(IApplicationThread caller, String callingPackage, String name, int userId, boolean stable) throws RemoteException {
        ContentProviderHolder res = super.getContentProvider(caller, callingPackage, name, userId, stable);
        if (res == null && ShimGmsFontProvider.AUTHORITY.equals(name)) {
            Log.d(TAG, "getContentProvider: returning ShimGmsFontProvider");
            var cph = new ContentProviderHolder(ShimGmsFontProvider.makeProviderInfo());
            cph.provider = new ShimGmsFontProvider();
            cph.noReleaseNeeded = true;
            cph.mLocal = true;
            return cph;
        }
        return res;
    }

    /** @see android.app.ContextImpl#startServiceCommon */
    @Override
    public ComponentName startService(IApplicationThread caller, Intent service, String resolvedType, boolean requireForeground, String callingPackage, String callingFeatureId, int userId) throws RemoteException {
        ComponentName res = super.startService(caller, service, resolvedType, requireForeground, callingPackage, callingFeatureId, userId);
        if (AppGlobals.getInitialPackageId() == PackageId.G_CAMERA) {
            if (res != null && "?".equals(res.getPackageName()) && service.getComponent() != null && service.getComponent().getClassName().endsWith(".NoOpPrewarmService")) {
                // NoOpPrewarmService is a performance optimization. GoogleCamera starts it from
                // background in some cases, which leads to a BackgroundServiceStartNotAllowedException
                // chain-crash.
                //
                // GoogleCamera has privileged integration on stock OS, it's always allowed to
                // start background services there.
                Log.d(TAG, "ignoring failed start of NoOpPrewarmService in GoogleCamera");
                return null;
            }
        }
        return res;
    }

}
