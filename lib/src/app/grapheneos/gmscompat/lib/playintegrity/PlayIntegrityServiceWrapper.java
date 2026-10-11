package app.grapheneos.gmscompat.lib.playintegrity;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.GosPackageState;
import android.content.pm.GosPackageStateFlag;
import android.ext.settings.app.AswBlockPlayIntegrityApi;
import android.os.Binder;
import android.os.BinderWrapper;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.gmscompat.GmsCompatApp;
import com.android.internal.os.BackgroundThread;

import static android.app.compat.gms.GmsCompat.appContext;
import static app.grapheneos.gmscompat.lib.playintegrity.PlayIntegrityUtils.isPlayIntegrityBlocked;

abstract class PlayIntegrityServiceWrapper extends BinderWrapper {
    final String TAG;
    protected int requestIntegrityTokenTxnCode;

    public PlayIntegrityServiceWrapper(IBinder base) {
        super(base);
        TAG = getClass().getSimpleName();
    }

    protected abstract Binder createTokenRequestStub();

    @Override
    public boolean transact(int code, Parcel data, @Nullable Parcel reply, int flags) throws RemoteException {
        if (code == requestIntegrityTokenTxnCode) {
            if (maybeStubOutIntegrityTokenRequest(code, data, reply, flags)) {
                return true;
            }
        }
        return super.transact(code, data, reply, flags);
    }

    private void onIntegrityTokenRequest(boolean isBlocked) {
        Runnable r = () -> {
            Context ctx = appContext();
            GosPackageState gosPs = GosPackageState.getForSelf(ctx);
            if (!gosPs.hasFlag(GosPackageStateFlag.PLAY_INTEGRITY_API_USED_AT_LEAST_ONCE)) {
                gosPs.createEditor(ctx.getPackageName(), ctx.getUser())
                        .addFlag(GosPackageStateFlag.PLAY_INTEGRITY_API_USED_AT_LEAST_ONCE)
                        .apply();
            }
            if (!AswBlockPlayIntegrityApi.I.isNotificationEnabled(gosPs)) {
                return;
            }
            try {
                GmsCompatApp.iClientOfGmsCore2Gca().voidCall(i ->
                        i.showPlayIntegrityNotification(ctx.getPackageName(), isBlocked));
            } catch (RemoteException e) {
                Log.e(TAG, "", e);
            }
        };
        BackgroundThread.getHandler().post(r);
    }

    private boolean maybeStubOutIntegrityTokenRequest(int code, Parcel data, @Nullable Parcel reply, int flags) {
        Log.d(TAG, "integrity token request detected");

        boolean isBlocked = isPlayIntegrityBlocked();
        onIntegrityTokenRequest(isBlocked);

        if (!isBlocked) {
            return false;
        }

        try {
            createTokenRequestStub().transact(code, data, reply, flags);
        } catch (RemoteException e) {
            // this is a local call
            throw new IllegalStateException(e);
        }
        return true;
    }

    protected static long getTokenRequestResultDelay() {
        return 500L;
    }
}
