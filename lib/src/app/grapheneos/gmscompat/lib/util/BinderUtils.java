package app.grapheneos.gmscompat.lib.util;

import android.annotation.Nullable;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class BinderUtils {
    private static final String TAG = "BinderUtils";

    @Nullable
    public static String getInterfaceDescriptor(IBinder binder) {
        try {
            return binder.getInterfaceDescriptor();
        } catch (RemoteException e) {
            Log.d(TAG, "", e);
            return null;
        }
    }
}
