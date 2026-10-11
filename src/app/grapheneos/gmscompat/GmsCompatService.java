package app.grapheneos.gmscompat;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class GmsCompatService extends Service {
    private static final String TAG = "GmsCompatService";

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return BinderGms2Gca.INSTANCE;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
    }
}
