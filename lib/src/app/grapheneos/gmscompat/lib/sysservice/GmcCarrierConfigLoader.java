package app.grapheneos.gmscompat.lib.sysservice;

import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.telephony.ICarrierConfigLoader;

class GmcCarrierConfigLoader extends ICarrierConfigLoader.Stub.Proxy {
    static final String TAG = "GmcCarrierConfigLoader";

    protected GmcCarrierConfigLoader(IBinder remote) {
        super(remote);
    }

    @Override
    public PersistableBundle getConfigForSubId(int subId, String callingPackage)
            throws RemoteException {
        try {
            return super.getConfigForSubId(subId, callingPackage);
        } catch (SecurityException e) {
            return nullCarrierConfig("getConfigForSubId", subId);
        }
    }

    @Override
    public PersistableBundle getConfigForSubIdWithFeature(int subId, String callingPackage,
            String callingFeatureId) throws RemoteException {
        try {
            return super.getConfigForSubIdWithFeature(subId, callingPackage, callingFeatureId);
        } catch (SecurityException e) {
            return nullCarrierConfig("getConfigForSubIdWithFeature", subId);
        }
    }

    @Override
    public PersistableBundle getConfigSubsetForSubIdWithFeature(int subId, String callingPackage,
            String callingFeatureId, String[] carrierConfigs) throws RemoteException {
        try {
            return super.getConfigSubsetForSubIdWithFeature(subId, callingPackage, callingFeatureId,
                    carrierConfigs);
        } catch (SecurityException e) {
            Log.d(TAG, "getConfigSubsetForSubIdWithFeature: returning empty carrier config for subId "
                    + subId);
            return new PersistableBundle();
        }
    }

    private static PersistableBundle nullCarrierConfig(String method, int subId) {
        Log.d(TAG, method + ": returning null carrier config for subId " + subId);
        return null;
    }
}
