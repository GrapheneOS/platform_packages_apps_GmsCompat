package app.grapheneos.gmscompat.lib.sysservice.client;

import android.app.IActivityManager;
import android.content.pm.IPackageManager;
import android.os.IBinder;
import android.os.IInterface;
import android.util.ArrayMap;

import java.util.function.Function;

public class ClientServiceOverridesRegistry {

    public static void init(ArrayMap<String, Function<IBinder, IInterface>> registry) {
        registry.put(IActivityManager.Stub.DESCRIPTOR, GclActivityManager::new);
        registry.put(IPackageManager.Stub.DESCRIPTOR, GclPackageManager::new);
    }
}
