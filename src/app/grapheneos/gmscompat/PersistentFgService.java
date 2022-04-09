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
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.internal.gmscompat.GmsInfo;

// raises priority of GMS Core and Play Store, thereby allowing them to start services when they need to
public class PersistentFgService extends Service {
    private static final String TAG = "PersistentFgService";

    private static final int GMS_CORE = 1;
    private static final int PLAY_STORE = 1 << 1;

    int boundPkgs;

    public void onCreate() {
        Notification.Builder nb = Notifications.Channel.PERSISTENT_FG_SERVICE.notifBuilder();
        nb.setSmallIcon(android.R.drawable.ic_dialog_dialer);
        nb.setContentTitle(getText(R.string.persistent_fg_service_notif));
        nb.setContentIntent(PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        nb.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        startForeground(Notifications.ID_PERSISTENT_FG_SERVICE, nb.build());
    }

    static void start(String callerPackage) {
        Context ctx = App.ctx();

        // call startForegroundService() from the main thread, not the binder thread
        new Handler(ctx.getMainLooper()).post(() -> {
            Intent i = new Intent(callerPackage);
            i.setClass(ctx, PersistentFgService.class);
            ctx.startForegroundService(i);
        });
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

    private boolean bindGmsCore() {
        // GmsDynamiteClientHooks expects "persistent" GMS Core process to be always running, take this into account
        // if this service becomes unavailable and needs to be replaced
        return bind(GMS_CORE, GmsInfo.PACKAGE_GMS_CORE, "com.google.android.gms.chimera.PersistentDirectBootAwareApiService");
    }

    private boolean bindPlayStore() {
        return bind(PLAY_STORE, GmsInfo.PACKAGE_PLAY_STORE, "com.google.android.finsky.ipcservers.main.MainGrpcServerAndroidService");
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
