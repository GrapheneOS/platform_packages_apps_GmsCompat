package app.grapheneos.gmscompat.lib.sysservice.client;

import android.app.ContentProviderHolder;
import android.app.IActivityManager;
import android.app.IApplicationThread;
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
}
