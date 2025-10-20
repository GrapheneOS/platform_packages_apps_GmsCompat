package app.grapheneos.gmscompat.lib;

import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.BinderProxy;
import android.os.IBinder;
import android.os.IInterface;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.gmscompat.IGmsCompatLib;

import java.io.FileDescriptor;
import java.util.Arrays;
import java.util.function.Function;

import app.grapheneos.gmscompat.lib.playintegrity.PlayIntegrityUtils;
import app.grapheneos.gmscompat.lib.sysservice.SystemServiceOverridesRegistry;
import app.grapheneos.gmscompat.lib.util.BinderUtils;

/**
 * GmsCompatLibrary code is loaded into processes of apps that use GmsCompat.
 */
public class GmsCompatLibImpl implements IGmsCompatLib {
    private static final String TAG = "GmcLib";

    @Override
    public void init(Context appContext, Context libContext, String processName) {
        Log.d(TAG, "init: pkg: " + appContext.getPackageName() + ", process: " + processName);
        SystemServiceOverridesRegistry.init(appContext, binderProxyOverridesRegistry);
    }

    @Override
    public ServiceConnection maybeReplaceServiceConnection(Intent service, long flags, UserHandle user, ServiceConnection orig) {
        ServiceConnection override = PlayIntegrityUtils.maybeReplaceServiceConnection(service, orig);
        return override;
    }

    private static final String TAG_BINDER = "GmcLibBinder";
    private final ArrayMap<String, Function<IBinder, IInterface>> binderProxyOverridesRegistry = new ArrayMap<>();

    @Nullable
    @Override
    public IInterface maybeProvideBinderProxyInterface(BinderProxy binderProxy, String ifaceDescriptor) {
        if (Log.isLoggable(TAG_BINDER, Log.DEBUG)) {
            Log.d(TAG_BINDER, "maybeProvideBinderProxyInterface: " + ifaceDescriptor + " | " + BinderUtils.getInterfaceDescriptor(binderProxy), maybeStackTrace(TAG_BINDER));
        }

        Function<IBinder, IInterface> creator = binderProxyOverridesRegistry.get(ifaceDescriptor);
        if (creator == null) {
            return null;
        }
        String actualIfaceDescriptor = BinderUtils.getInterfaceDescriptor(binderProxy);
        if (!ifaceDescriptor.equals(actualIfaceDescriptor)) {
            Log.w(TAG, "interface descriptor mismatch: expected " + ifaceDescriptor + " got " + actualIfaceDescriptor);
            return null;
        }
        return creator.apply(binderProxy);
    }

    @Nullable
    private static Throwable maybeStackTrace(String tag) {
        return Log.isLoggable(tag, Log.VERBOSE) ? new Throwable() : null;
    }
}
