package app.grapheneos.gmscompat.lib.sysservice;

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.IActivityManager;
import android.app.IApplicationThread;
import android.app.compat.gms.GmsCompat;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.gmscompat.GmsCompatApp;

class GmcActivityManager extends IActivityManager.Stub.Proxy {
    static final String TAG = "GmcActivityManager";

    protected GmcActivityManager(IBinder remote) {
        super(remote);
    }

    @Override
    public ComponentName startService(IApplicationThread caller, Intent service, String resolvedType, boolean requireForeground, String callingPackage, String callingFeatureId, int userId) throws RemoteException {
        try {
            ComponentName res = super.startService(caller, service, resolvedType, requireForeground, callingPackage, callingFeatureId, userId);
            if (res == null || !res.getPackageName().equals("?")) {
                return res;
            }
        } catch (ForegroundServiceStartNotAllowedException e) {
            Log.d(TAG, "", e);
        }
        raiseSelfToForeground(service, requireForeground);
        return super.startService(caller, service, resolvedType, requireForeground, callingPackage, callingFeatureId, userId);
    }

    private static void raiseSelfToForeground(Intent service, boolean requireForeground) {
        Log.d(TAG, "unable to start " + service + ", requireForeground: " + requireForeground);
        String reason = "GmsCompat: " + service + ", requireForeground: " + requireForeground;
        GmsCompatApp.raisePackageToForeground(GmsCompat.appContext().getPackageName(),
                                30_000, reason, android.os.PowerExemptionManager.REASON_OTHER);
    }
}
