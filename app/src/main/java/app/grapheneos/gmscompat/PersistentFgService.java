package app.grapheneos.gmscompat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import static app.grapheneos.gmscompat.Const.GSF_PKG;
import static app.grapheneos.gmscompat.Const.PLAY_SERVICES_PKG;
import static app.grapheneos.gmscompat.Const.PLAY_STORE_PKG;

// raises priority of Play services and Play Store, thereby allowing them to start services when they need to
public class PersistentFgService extends Service {
    private static final String TAG = "PersistentFgService";

    private static final int PLAY_SERVICES = 1;
    private static final int PLAY_STORE = 1 << 1;

    int boundPkgs;

    public void onCreate() {
        String title = getString(R.string.persistent_fg_service_notif);
        NotificationChannel nc = new NotificationChannel(App.NotificationChannels.PERSISTENT_FG_SERVICE, title, NotificationManager.IMPORTANCE_LOW);
        nc.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(nc);

        Notification.Builder nb = new Notification.Builder(this, App.NotificationChannels.PERSISTENT_FG_SERVICE);
        nb.setSmallIcon(android.R.drawable.ic_dialog_dialer);
        nb.setContentTitle(title);
        nb.setContentIntent(PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        nb.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        startForeground(App.NotificationIds.PERSISTENT_FG_SERVICE, nb.build());
    }

    static void start(Context componentCtx, String callerPackage) {
        Context ctx = componentCtx.getApplicationContext();

        // call startForegroundService() from the main thread, not the binder thread
        // (this method is called from ContentProvider.call())
        new Handler(ctx.getMainLooper()).post(() -> {
            Intent i = new Intent(callerPackage);
            i.setClass(ctx, PersistentFgService.class);
            ctx.startForegroundService(i);
        });
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.d(TAG, "received null intent, rebinding services");
            bindPlayServices();
            bindPlayStore();
        } else {
            String pkg = intent.getAction();
            boolean res;
            if (PLAY_SERVICES_PKG.equals(pkg)) {
                res = bindPlayServices();
            } else if (PLAY_STORE_PKG.equals(pkg)) {
                res = bindPlayStore();
            } else if (GSF_PKG.equals(pkg)) {
                // GSF doesn't need to be bound, but needs :persistent process to remain running to
                // be able to use its exported binder
                res = true;
            } else {
                // this service is protected by the signature-level permission
                throw new SecurityException("unauthorized intent " + intent);
            }
            if (!res) {
                Log.w(TAG, "unable to bind to " + pkg);
            }
        }
        return START_STICKY;
    }

    private boolean bindPlayServices() {
        // GmsDynamiteClientHooks expects "persistent" Play services process to be always running, take this into account
        // if this service becomes unavailable and needs to be replaced
        return bind(PLAY_SERVICES, PLAY_SERVICES_PKG, "com.google.android.gms.chimera.PersistentDirectBootAwareApiService");
    }

    private boolean bindPlayStore() {
        return bind(PLAY_STORE, PLAY_STORE_PKG, "com.google.android.finsky.ipcservers.main.MainGrpcServerAndroidService");
    }
    // it's important that both of these services are directBootAware,
    // keep that in mind if they become unavailable and need to be replaced

    private boolean bind(int pkgId, String pkg, String cls) {
        if ((boundPkgs & pkgId) != 0) {
            Log.d(TAG, pkg + " is already bound");
            return true;
        }
        Intent i = new Intent();
        i.setClassName(pkg, cls);

        // BIND_INCLUDE_CAPABILITIES isn't needed, at least for now
        int flags = BIND_AUTO_CREATE | BIND_IMPORTANT;
        boolean r = bindService(i, new Connection(pkgId, this), flags);
        if (r) {
            boundPkgs |= pkgId;
        }
        return r;
    }

    static class Connection implements ServiceConnection {
        final int pkgId;
        final PersistentFgService svc;

        Connection(int pkgId, PersistentFgService svc) {
            this.pkgId = pkgId;
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
            svc.boundPkgs &= ~pkgId;
        }

        public void onNullBinding(ComponentName name) {
            throw new IllegalStateException("unable to bind " + name);
        }
    }

    @Nullable public IBinder onBind(Intent intent) { return null; }
}
