package app.grapheneos.gmscompat.lib;

import android.annotation.Nullable;
import android.app.appsearch.safeparcel.SafeParcelReader;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.BinderProxy;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.gmscompat.IGmsCompatLib;

import com.google.android.gms.constellation.VerifyPhoneNumberRequest;

import java.io.FileDescriptor;
import java.text.ParseException;
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

    @Override
    public boolean maybeInterceptBinderProxyDump(BinderProxy binderProxy, FileDescriptor fd, String[] args, boolean async) {
        Log.i(TAG_BINDER, "maybeInterceptBinderProxyDump: " + BinderUtils.getInterfaceDescriptor(binderProxy) + " " + Arrays.toString(args), maybeStackTrace(TAG_BINDER));
        return false;
    }

    @Nullable
    @Override
    public String parseVerifyPhoneNumberRequestForPolicy(Parcel data) throws ParseException {
        // Note: VerifyPhoneNumberRequest may contain sensitive information (IMSIs), but
        // we're only reading the policy ID.
        try {
            final VerifyPhoneNumberRequest request = VerifyPhoneNumberRequest.CREATOR
                    .createFromParcel(data);
            return request.policyId;
        } catch (Exception e) {
            Log.w(TAG, "failed to parse VerifyPhoneNumberRequest", e);
            if (e instanceof SafeParcelReader.ParseException) {
                throw new ParseException(e.getMessage(), data.dataPosition());
            }
            throw e;
        }
    }

    @Nullable
    private static Throwable maybeStackTrace(String tag) {
        return Log.isLoggable(tag, Log.VERBOSE) ? new Throwable() : null;
    }
}
