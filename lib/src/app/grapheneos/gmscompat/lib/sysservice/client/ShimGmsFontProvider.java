package app.grapheneos.gmscompat.lib.sysservice.client;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.compat.gms.GmsCompat;
import android.content.AttributionSource;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.ext.PackageId;
import android.net.Uri;
import android.os.Bundle;
import android.os.ICancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;

import app.grapheneos.gmscompat.lib.util.BaseIContentProvider;

// This provider prevents crashes of apps that depend on GmsCore font provider when GmsCore is
// missing or disabled.
public class ShimGmsFontProvider extends BaseIContentProvider {
    static final String TAG = "ShimGmsFontProvider";
    static final String AUTHORITY = "com.google.android.gms.fonts";

    static ProviderInfo makeProviderInfo() {
        var res = new ProviderInfo();
        res.packageName = PackageId.GMS_CORE_NAME;
        res.name = AUTHORITY;
        res.authority = AUTHORITY;
        res.applicationInfo = GmsCompat.appContext().getApplicationInfo();
        res.applicationInfo.packageName = res.packageName;
        return res;
    }

    // randomly generated ID
    private static final String FONT_FILE_ID = "463606443";

    @Override
    public Cursor query(@NonNull AttributionSource attributionSource, Uri url, @Nullable String[] projection, @Nullable Bundle queryArgs, @Nullable ICancellationSignal cancellationSignal) throws RemoteException {
        Log.d(TAG, "query: " + url.toString() + " projection: " + Arrays.toString(projection) + " args: " + (queryArgs != null ? queryArgs.toStringDeep() : ""));
        var cursor = new MatrixCursor(new String[] { "file_id" });
        cursor.addRow(new Object[] { FONT_FILE_ID });
        return cursor;
    }

    @Override
    public AssetFileDescriptor openTypedAssetFile(@NonNull AttributionSource attributionSource, Uri url, String mimeType, Bundle opts, ICancellationSignal signal) throws RemoteException, FileNotFoundException {
        Log.d(TAG, "openTypedAssetFile: " + url + " " + url.getPath() + " mimeType: " + mimeType + " opts: " + (opts != null ? opts.toStringDeep() : ""));
        if (!("/file/" + FONT_FILE_ID).equals(url.getPath())) {
            throw new FileNotFoundException();
        }
        var file = new File("/system/fonts/Roboto-Regular.ttf");
        var pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(pfd, 0L, AssetFileDescriptor.UNKNOWN_LENGTH);
    }
}
