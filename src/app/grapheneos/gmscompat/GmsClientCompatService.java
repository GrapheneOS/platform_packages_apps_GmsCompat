package app.grapheneos.gmscompat;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class GmsClientCompatService extends Service {
    private static final String TAG = "GmsClientCompatService";

    private BinderClientOfGmsCore2Gca binder;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        binder = new BinderClientOfGmsCore2Gca(getApplicationContext());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
    }
}
