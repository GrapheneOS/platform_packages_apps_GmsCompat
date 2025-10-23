package app.grapheneos.gmscompat.lib.sysservice.client;

import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.Signature;
import android.ext.PackageId;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import app.grapheneos.gmscompat.lib.util.Certs;

class GclPackageManager extends IPackageManager.Stub.Proxy {
    static final String TAG = "GclPackageManager";

    GclPackageManager(IBinder remote) { super(remote); }

    @Override
    public ProviderInfo resolveContentProvider(String name, long flags, int userId) throws RemoteException {
        ProviderInfo res = super.resolveContentProvider(name, flags, userId);
        if (res == null && ShimGmsFontProvider.AUTHORITY.equals(name)) {
            Log.d(TAG, "resolveContentProvider: providing ShimGmsFontProvider info");
            return ShimGmsFontProvider.makeProviderInfo();
        }
        return res;
    }

    @Override
    public PackageInfo getPackageInfo(String packageName, long flags, int userId) throws RemoteException {
        PackageInfo res = super.getPackageInfo(packageName, flags, userId);

        //noinspection deprecation
        if (res == null && PackageId.GMS_CORE_NAME.equals(packageName) && flags == PackageManager.GET_SIGNATURES) {
            Log.d(TAG, "getPackageInfo: providing GmsCore cert info");
            var pkgInfo = new PackageInfo();
            //noinspection deprecation
            pkgInfo.signatures = new Signature[] { Certs.gmsCore() };
            return pkgInfo;
        }
        return res;
    }
}
