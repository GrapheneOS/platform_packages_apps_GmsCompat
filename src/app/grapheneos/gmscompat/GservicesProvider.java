package app.grapheneos.gmscompat;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.ext.PackageId;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.app.RedirectedContentProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

public class GservicesProvider extends RedirectedContentProvider {
    private static final String LOG_TAG = "GmsCompatGservices";

    private static final String GSERVICES_AUTHORITY = "com.google.android.gsf.gservices";
    private static final File FLAGS_DIR =
            new File("/system_ext/etc/gmscompat/gservices-flags");
    private static final String FLAGS_FILE_NAME = "flags.txt";
    private static final String[] COLUMNS = {"key", "value"};

    private volatile ArrayMap<String, String> flags;

    @Override
    public boolean onCreate() {
        TAG = LOG_TAG;
        authorityOverride = GSERVICES_AUTHORITY;
        return super.onCreate();
    }

    @Nullable
    @Override
    public Cursor queryInner(@NonNull Uri uri, @Nullable String[] projection,
            @Nullable String selection, @Nullable String[] selectionArgs,
            @Nullable String sortOrder) {
        enforceCallerAllowed();

        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        ArrayMap<String, String> flags = getFlags();
        boolean isPrefixQuery = isPrefixQuery(uri);
        if (isPrefixQuery) {
            addPrefixRows(cursor, flags, selectionArgs);
        } else {
            addSingleRow(cursor, flags, selectionArgs);
        }
        maybeLogRedirectedRead(isPrefixQuery, selectionArgs, cursor.getCount());
        return cursor;
    }

    private void addSingleRow(MatrixCursor cursor, ArrayMap<String, String> flags,
            @Nullable String[] selectionArgs) {
        if (selectionArgs == null || selectionArgs.length == 0) {
            return;
        }

        String key = selectionArgs[0];
        String value = flags.get(key);
        if (value != null) {
            cursor.newRow().add(key).add(value);
        }
    }

    private void addPrefixRows(MatrixCursor cursor, ArrayMap<String, String> flags,
            @Nullable String[] selectionArgs) {
        if (selectionArgs == null || selectionArgs.length == 0) {
            return;
        }

        // GmsCore returns Gservices /prefix rows in key order. EuiccSupportPixel copies rows
        // into a map before using them, so this compatibility provider does not preserve
        // that ordering.
        for (int i = 0, size = flags.size(); i < size; ++i) {
            String key = flags.keyAt(i);
            if (startsWithAny(key, selectionArgs)) {
                cursor.newRow().add(key).add(flags.valueAt(i));
            }
        }
    }

    private static boolean startsWithAny(String key, String[] prefixes) {
        for (String prefix : prefixes) {
            if (prefix != null && key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPrefixQuery(Uri uri) {
        return uri.getPathSegments().size() == 1 && "prefix".equals(uri.getPathSegments().get(0));
    }

    private static void maybeLogRedirectedRead(boolean isPrefixQuery,
            @Nullable String[] selectionArgs, int rowCount) {
        if (!Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
            return;
        }

        Log.v(LOG_TAG, "redirected Gservices read"
                + ": uid=" + Binder.getCallingUid()
                + ", type=" + (isPrefixQuery ? "prefix" : "key")
                + ", args=" + Arrays.toString(selectionArgs)
                + ", rows=" + rowCount);
    }

    private void enforceCallerAllowed() {
        int callingUid = Binder.getCallingUid();
        if (callingUid == Process.SYSTEM_UID || callingUid == Process.myUid()) {
            return;
        }

        Context ctx = getContext();
        if (ctx == null) {
            throw new SecurityException("missing provider context");
        }

        PackageManager pm = ctx.getPackageManager();
        String[] packages = pm.getPackagesForUid(callingUid);
        if (packages != null) {
            String selfPackage = ctx.getPackageName();
            int userId = UserHandle.getUserId(callingUid);
            for (String packageName : packages) {
                if (selfPackage.equals(packageName)
                        || hasPackageId(pm, packageName, callingUid, userId,
                                PackageId.EUICC_SUPPORT_PIXEL)) {
                    return;
                }
            }
        }

        Log.w(LOG_TAG, "rejecting query from uid " + callingUid);
        throw new SecurityException("caller is not allowed to query Gservices flags");
    }

    private static boolean hasPackageId(PackageManager pm, String packageName, int callingUid,
            int userId, int packageId) {
        try {
            ApplicationInfo appInfo = pm.getApplicationInfoAsUser(packageName, 0, userId);
            return appInfo.uid == callingUid && appInfo.ext().getPackageId() == packageId;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private ArrayMap<String, String> getFlags() {
        ArrayMap<String, String> res = flags;
        if (res != null) {
            return res;
        }

        synchronized (this) {
            res = flags;
            if (res == null) {
                res = loadFlags();
                flags = res;
            }
        }
        return res;
    }

    private static ArrayMap<String, String> loadFlags() {
        File file = selectFlagsFile();
        if (file == null) {
            return new ArrayMap<>();
        }

        ArrayMap<String, String> res = new ArrayMap<>();
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                readFlagLine(line, res);
            }
        } catch (IOException e) {
            Log.w(LOG_TAG, "failed to read " + file, e);
            return new ArrayMap<>();
        }
        return res;
    }

    @Nullable
    private static File selectFlagsFile() {
        File commonFile = new File(FLAGS_DIR, FLAGS_FILE_NAME);
        if (commonFile.isFile()) {
            return commonFile;
        }

        File skuFile = getSkuFlagsFile(Build.SKU);
        if (skuFile != null) {
            return skuFile;
        }

        if (!Build.ODM_SKU.equals(Build.SKU)) {
            return getSkuFlagsFile(Build.ODM_SKU);
        }

        return null;
    }

    @Nullable
    private static File getSkuFlagsFile(String sku) {
        if (sku == null || sku.isEmpty() || Build.UNKNOWN.equals(sku)) {
            return null;
        }

        File file = new File(new File(FLAGS_DIR, sku), FLAGS_FILE_NAME);
        return file.isFile() ? file : null;
    }

    private static void readFlagLine(String line, ArrayMap<String, String> flags) {
        String row = line.stripLeading();
        if (row.isEmpty() || row.startsWith("#")) {
            return;
        }

        int nameEnd = firstWhitespaceIndex(row);
        if (nameEnd <= 0) {
            return;
        }

        String key = row.substring(0, nameEnd);
        flags.put(key, row.substring(nameEnd + 1));
    }

    private static int firstWhitespaceIndex(String value) {
        int length = value.length();
        for (int i = 0; i < length; ++i) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
