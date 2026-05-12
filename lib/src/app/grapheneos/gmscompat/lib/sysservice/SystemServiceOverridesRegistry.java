package app.grapheneos.gmscompat.lib.sysservice;

import android.app.ActivityManager;
import android.app.ActivityThread;
import android.app.AppGlobals;
import android.app.IActivityManager;
import android.app.SystemServiceRegistry;
import android.app.compat.gms.GmsCompat;
import android.content.Context;
import android.ext.PackageId;
import android.hardware.display.DisplayManagerGlobal;
import android.os.IBinder;
import android.os.IInterface;
import android.util.ArrayMap;

import com.android.internal.telephony.ICarrierConfigLoader;

import java.util.function.Function;

import app.grapheneos.gmscompat.lib.GmsCompatLibImpl;
import app.grapheneos.gmscompat.lib.sysservice.client.ClientServiceOverridesRegistry;

public class SystemServiceOverridesRegistry {

    public static void init(Context appContext, ArrayMap<String, Function<IBinder, IInterface>> registry) {
        // Clear cached binder wrappers to support overriding them through
        /** @see GmsCompatLibImpl#maybeProvideBinderProxyInterface */
        // Few wrappers are cached at this point and all or almost all of them are backed by cached
        // binders, i.e. their reinitialization will happen locally, without IPC
        ActivityManager.clearCachedService();
        ActivityThread.clearCachedPackageManager();
        DisplayManagerGlobal.clearCachedInstance();
        SystemServiceRegistry.clearServiceCache(appContext);

        if (GmsCompat.isEnabled()) {
            registry.put(IActivityManager.Stub.DESCRIPTOR, GmcActivityManager::new);
            if (GmsCompat.isAndroidAuto()) {
                registry.put(ICarrierConfigLoader.Stub.DESCRIPTOR, GmcCarrierConfigLoader::new);
            }
        } else if (AppGlobals.getInitialPackageId() == PackageId.G_CAMERA) {
            ClientServiceOverridesRegistry.init(registry);
        }
    }
}
