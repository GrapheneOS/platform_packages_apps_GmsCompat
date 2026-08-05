package app.grapheneos.gmscompat;

import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.PowerExemptionManager;
import android.util.Log;

import com.android.internal.gmscompat.client.GmsCompatClientService;
import com.android.internal.infra.AndroidFuture;
import com.android.internal.os.BackgroundThread;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class TempServiceBinding implements ServiceConnection, Runnable {
    private static final AtomicLong idSrc = new AtomicLong();

    private final String TAG = TempServiceBinding.class.getSimpleName() + ':' + idSrc.getAndIncrement();

    private final AndroidFuture<Boolean> connectionResult = new AndroidFuture<>();

    private TempServiceBinding() {}

    public static void create(String targetPkg, long durationMs, @Nullable String reason, int reasonCode,
                              @Nullable String serviceClassName) {
        if (durationMs < 0) {
            throw new IllegalArgumentException(Long.toString(durationMs));
        }

        var instance = new TempServiceBinding();
        String TAG = instance.TAG;

        Log.d(TAG, "create: pkgName " + targetPkg
            + ", duration: " + durationMs
            + ", reason: " + reason
            + ", reasonCode: " + PowerExemptionManager.reasonCodeToString(reasonCode));

        var intent = new Intent();
        intent.setClassName(targetPkg, serviceClassName != null ?
                serviceClassName : GmsCompatClientService.class.getName());

        Context appContext = App.ctx();
        if (!appContext.bindService(intent, Context.BIND_AUTO_CREATE, BackgroundThread.getExecutor(), instance)) {
            Log.e(TAG, "bindService() returned false");
            appContext.unbindService(instance);
            return;
        }

        if (durationMs > 0) {
            // unbind the service after timeout, see run() below
            BackgroundThread.getHandler().postDelayed(instance, durationMs);
        }

        try {
            Boolean res = instance.connectionResult.get(5, TimeUnit.SECONDS);
            if (!res.booleanValue()) {
                Log.e(TAG, "connectionResult is false");
            }
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.d(TAG, "onConnected latch completed");
        }
    }

    @Override
    public void run() {
        Log.d(TAG, "timeout expired, unbinding");
        unbind();
    }

    private boolean unbound;

    private void unbind() {
        if (!connectionResult.isDone()) {
            connectionResult.complete(Boolean.FALSE);
        }

        if (unbound) {
            Log.d(TAG, "already unbound");
        } else {
            App.ctx().unbindService(this);
            Log.d(TAG, "unbind");
            unbound = true;
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onServiceConnected");
        }
        connectionResult.complete(Boolean.TRUE);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.e(TAG, "onServiceDisconnected");
    }

    @Override
    public void onBindingDied(ComponentName name) {
        Log.d(TAG, "onBindingDied");
        unbind();
    }

    @Override
    public void onNullBinding(ComponentName name) {
        Log.e(TAG, "onNullBinding");
        unbind();
    }
}
