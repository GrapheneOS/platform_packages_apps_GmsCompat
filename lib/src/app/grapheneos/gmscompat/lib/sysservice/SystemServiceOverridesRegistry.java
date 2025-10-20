package app.grapheneos.gmscompat.lib.sysservice;

import android.app.ActivityManager;
import android.app.ActivityThread;
import android.app.IActivityManager;
import android.app.SystemServiceRegistry;
import android.app.compat.gms.GmsCompat;
import android.content.Context;
import android.content.pm.IPackageManager;
import android.hardware.display.DisplayManagerGlobal;
import android.os.IBinder;
import android.os.IInterface;
import android.util.ArrayMap;

import java.util.function.Function;

public class SystemServiceOverridesRegistry {

    public static void init(Context appContext, ArrayMap<String, Function<IBinder, IInterface>> registry) {
        // Clear cached binder wrappers to support overriding them through the
        // maybeProvideBinderProxyInterface() below.
        // Few wrappers are cached at this point and all or almost all of them are backed by cached
        // binders, i.e. their reinitialization will happen locally, without IPC
        ActivityManager.clearCachedService();
        ActivityThread.clearCachedPackageManager();
        DisplayManagerGlobal.clearCachedInstance();
        SystemServiceRegistry.clearServiceCache(appContext);
    }
}
