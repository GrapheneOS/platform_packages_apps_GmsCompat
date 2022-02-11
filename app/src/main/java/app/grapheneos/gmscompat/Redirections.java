package app.grapheneos.gmscompat;

import android.Manifest.permission;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;

import java.util.Arrays;

import app.grapheneos.gmscompat.App.MainProcessPrefs;
import app.grapheneos.gmscompat.location.GLocationService;

public class Redirections {
    static final int ID_GoogleLocationManagerService = 0;
    private static final int ID_COUNT = 1;

    private static String[] redirectableInterfaces;
    private static Object lock;

    private static long enabledIds;

    static void init(SharedPreferences prefs) {
        enabledIds = prefs.getLong(MainProcessPrefs.ENABLED_REDIRECTIONS, 0);

        long inited = prefs.getLong(MainProcessPrefs.INITED_REDIRECTIONS, 0);
        boolean changed = false;
        for (int i = 0; i < Redirections.ID_COUNT; ++i) {
            long flag = 1L << i;
            if ((inited & flag) == 0) {
                if (Redirections.isRedirectionEnabledByDefault(i)) {
                    enabledIds |= flag;
                }
                inited |= flag;
                changed = true;
            }
        }
        if (changed) {
            SharedPreferences.Editor ed = prefs.edit();
            ed.putLong(MainProcessPrefs.INITED_REDIRECTIONS, inited);
            ed.putLong(MainProcessPrefs.ENABLED_REDIRECTIONS, enabledIds);
            ed.apply();
        }
        {
            String[] arr = new String[ID_COUNT];
            arr[ID_GoogleLocationManagerService] =
                "com.google.android.gms.location.internal.IGoogleLocationManagerService";
            if (Const.DEV) {
                // make sure array is sorted
                String[] clone = arr.clone();
                Arrays.sort(clone);
                assert Arrays.equals(arr, clone);
            }
            redirectableInterfaces = arr;
        }
        lock = new Object();
    }

    private static boolean isRedirectionEnabledByDefault(int id) {
        switch (id) {
            case ID_GoogleLocationManagerService:
                return !UtilsKt.playServicesHasPermission(permission.ACCESS_BACKGROUND_LOCATION);
            default:
                return false;
        }
    }

    public static boolean isEnabled(int id) {
        UtilsKt.mainProcess();
        synchronized (lock) {
            return (enabledIds & (1L << id)) != 0;
        }
    }

    public static void setState(int id, boolean enabled) {
        UtilsKt.mainProcess();

        synchronized (lock) {
            long flag = 1L << id;
            enabledIds = (enabledIds & (~ flag)) | (enabled? flag : 0);
            SharedPreferences.Editor ed = App.preferences().edit();
            ed.putLong(MainProcessPrefs.ENABLED_REDIRECTIONS, enabledIds);
            ed.apply();
        }

        Intent broadcast = new Intent(Const.PKG_NAME + ".ACTION_REDIRECTION_STATE_CHANGED");
        final String KEY_REDIRECTION_ID = "id";
        broadcast.putExtra(KEY_REDIRECTION_ID, id);
        App.ctx().sendBroadcast(broadcast);
    }

    static Bundle getInterfaces() {
        Bundle res = new Bundle(1);
        res.putStringArray(GmsClientProvider.KEY_RESULT, redirectableInterfaces);
        return res;
    }

    static Bundle getRedirector(int id) {
        if (!isEnabled(id)) {
            return null;
        }
        IBinder binder;
        int[] txnCodes;
        switch (id) {
            case ID_GoogleLocationManagerService:
                binder = GLocationService.INSTANCE;
                txnCodes = GLocationService.CODES;
                break;
            default:
                throw new IllegalStateException("unknown id " + id);
        }
        Bundle res = new Bundle(2);
        res.putBinder(GmsClientProvider.KEY_BINDER, binder);
        res.putIntArray(GmsClientProvider.KEY_BINDER_TRANSACTION_CODES, txnCodes);
        return res;
    }
}
