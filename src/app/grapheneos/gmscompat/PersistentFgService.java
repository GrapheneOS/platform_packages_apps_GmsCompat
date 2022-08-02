package app.grapheneos.gmscompat;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.internal.gmscompat.GmsInfo;
import com.android.internal.gmscompat.client.GmsCompatClientService;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// raises priority of GMS Core and Play Store, thereby allowing them to start services when they need to
public class PersistentFgService extends Service {
    private static final String TAG = "PersistentFgService";

    private static final ArrayMap<ParcelUuid, CountDownLatch> pendingLatches = new ArrayMap<>(5);
    private static final ArraySet<CountDownLatch> completedLatches = new ArraySet<>(5);

    private static final String EXTRA_ID = "id";

    final ArraySet<String> boundPackages = new ArraySet<>();

    public void onCreate() {
        Notification.Builder nb = Notifications.builder(Notifications.CH_PERSISTENT_FG_SERVICE);
        nb.setSmallIcon(android.R.drawable.ic_dialog_dialer);
        nb.setContentTitle(getText(R.string.persistent_fg_service_notif));
        nb.setContentIntent(PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        nb.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        startForeground(Notifications.ID_PERSISTENT_FG_SERVICE, nb.build());
    }

    static void start(String callerPackage, String processName) {
        Context ctx = App.ctx();

        ParcelUuid uuid = new ParcelUuid(UUID.randomUUID());

        Intent intent = new Intent(callerPackage);
        intent.setClass(ctx, PersistentFgService.class);
        intent.putExtra(EXTRA_ID, uuid);

        CountDownLatch latch = new CountDownLatch(1);
        synchronized (pendingLatches) {
            pendingLatches.put(uuid, latch);
        }

        // call startForegroundService() from the main thread, not the binder thread
        new Handler(ctx.getMainLooper()).post(() -> {
            ctx.startForegroundService(intent);
        });

        Log.d(TAG, "caller " + callerPackage + ", processName " + processName +  ", UUID " + uuid);

        try {
            // make sure priority of the caller is raised by bindService() before returning, otherwise
            // startService() by caller may throw BackgroundServiceStartNotAllowedException if it wins the race
            // against startForegroundService() + bindService() in this process
            if (latch.await(30, TimeUnit.SECONDS)) {
                synchronized (completedLatches) {
                    if (!completedLatches.remove(latch)) {
                        throw new IllegalStateException("binding failed, UUID " + uuid);
                    }
                }
            } else {
                throw new IllegalStateException("waiting for binding timed out, UUID " + uuid);
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.d(TAG, "received null intent, rebinding services");
            bindGmsCore();
            bindPlayStore();
        } else {
            String pkg = intent.getAction();
            boolean res;
            if (GmsInfo.PACKAGE_GMS_CORE.equals(pkg)) {
                res = bindGmsCore();
            } else if (GmsInfo.PACKAGE_PLAY_STORE.equals(pkg)) {
                res = bindPlayStore();
            } else if (GmsInfo.PACKAGE_GSF.equals(pkg)) {
                // GSF doesn't need to be bound, but needs GmsCompatApp process to remain running to
                // be able to use its exported binder
                res = true;
            } else if (GmsInfo.PACKAGE_GSA.equals(pkg)) {
                res = bind(pkg, GmsCompatClientService.class.getName());
            } else {
                // this service is not exported
                throw new IllegalStateException("unexpected intent action " + pkg);
            }

            notifyCaller(intent, res);
        }
        return START_STICKY;
    }

    private static void notifyCaller(Intent intent, boolean res) {
        ParcelUuid uuid = intent.getParcelableExtra(EXTRA_ID);
        CountDownLatch latch;
        synchronized (pendingLatches) {
            latch = pendingLatches.remove(uuid);
        }

        if (latch == null) {
            // returning START_STICKY guarantees that intent will be delivered at most once
            throw new IllegalStateException("latch == null, UUID " + uuid);
        }

        if (res) {
            synchronized (completedLatches) {
                completedLatches.add(latch);
            }
        } else {
            Log.e(TAG, "binding failed, UUID " + uuid);
        }

        latch.countDown();
    }

    private boolean bindGmsCore() {
        // GmsDynamiteClientHooks expects "persistent" GMS Core process to be always running, take this into account
        // if this service becomes unavailable and needs to be replaced
        return bind(GmsInfo.PACKAGE_GMS_CORE, "com.google.android.gms.chimera.PersistentDirectBootAwareApiService");
    }

    private boolean bindPlayStore() {
        return bind(GmsInfo.PACKAGE_PLAY_STORE, GmsCompatClientService.class.getName());
    }
    // it's important that both of these services are directBootAware,
    // keep that in mind if they become unavailable and need to be replaced

    private boolean bind(String pkg, String cls) {
        if (boundPackages.contains(pkg)) {
            Log.i(TAG, pkg + " is already bound");
            return true;
        }
        Intent i = new Intent();
        i.setClassName(pkg, cls);

        // BIND_INCLUDE_CAPABILITIES isn't needed, at least for now
        int flags = BIND_AUTO_CREATE | BIND_IMPORTANT;
        boolean r = bindService(i, new Connection(pkg, this), flags);
        if (r) {
            boundPackages.add(pkg);
        }
        return r;
    }

    static class Connection implements ServiceConnection {
        final String pkg;
        final PersistentFgService svc;

        Connection(String pkg, PersistentFgService svc) {
            this.pkg = pkg;
            this.svc = svc;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected " + name);
        }

        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected " + name);
        }

        public void onBindingDied(ComponentName name) {
            Log.d(TAG, "onBindingDied " + name);
            // see the onBindingDied doc
            svc.unbindService(this);
            svc.boundPackages.remove(pkg);
        }

        public void onNullBinding(ComponentName name) {
            svc.unbindService(this);

            String msg = "unable to bind " + name;
            if (pkg.equals(GmsInfo.PACKAGE_GMS_CORE) || pkg.equals(GmsInfo.PACKAGE_PLAY_STORE)) {
                throw new IllegalStateException(msg);
            } else {
                Log.e(TAG, msg);
            }
        }
    }

    @Nullable public IBinder onBind(Intent intent) { return null; }
}
