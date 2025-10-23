package app.grapheneos.gmscompat.lib.util;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.AttributionSource;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.IContentProvider;
import android.content.OperationApplicationException;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ICancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.RemoteCallback;
import android.os.RemoteException;

import java.io.FileNotFoundException;
import java.util.ArrayList;

public class BaseIContentProvider implements IContentProvider {

    @Override
    public Cursor query(@NonNull AttributionSource attributionSource, Uri url, @Nullable String[] projection, @Nullable Bundle queryArgs, @Nullable ICancellationSignal cancellationSignal) throws RemoteException {
        return null;
    }

    @Override
    public String getType(@NonNull AttributionSource attributionSource, Uri url) throws RemoteException {
        return "";
    }

    @Override
    public void getTypeAsync(@NonNull AttributionSource attributionSource, Uri url, RemoteCallback callback) throws RemoteException {

    }

    @Override
    public void getTypeAnonymousAsync(Uri uri, RemoteCallback callback) throws RemoteException {

    }

    @Override
    public Uri insert(@NonNull AttributionSource attributionSource, Uri url, ContentValues initialValues, Bundle extras) throws RemoteException {
        return null;
    }

    @Override
    public int bulkInsert(@NonNull AttributionSource attributionSource, Uri url, ContentValues[] initialValues) throws RemoteException {
        return 0;
    }

    @Override
    public int delete(@NonNull AttributionSource attributionSource, Uri url, Bundle extras) throws RemoteException {
        return 0;
    }

    @Override
    public int update(@NonNull AttributionSource attributionSource, Uri url, ContentValues values, Bundle extras) throws RemoteException {
        return 0;
    }

    @Override
    public ParcelFileDescriptor openFile(@NonNull AttributionSource attributionSource, Uri url, String mode, ICancellationSignal signal) throws RemoteException, FileNotFoundException {
        return null;
    }

    @Override
    public AssetFileDescriptor openAssetFile(@NonNull AttributionSource attributionSource, Uri url, String mode, ICancellationSignal signal) throws RemoteException, FileNotFoundException {
        return null;
    }

    @Override
    public ContentProviderResult[] applyBatch(@NonNull AttributionSource attributionSource, String authority, ArrayList<ContentProviderOperation> operations) throws RemoteException, OperationApplicationException {
        return new ContentProviderResult[0];
    }

    @Override
    public Bundle call(@NonNull AttributionSource attributionSource, String authority, String method, @Nullable String arg, @Nullable Bundle extras) throws RemoteException {
        return null;
    }

    @Override
    public int checkUriPermission(@NonNull AttributionSource attributionSource, Uri uri, int uid, int modeFlags) throws RemoteException {
        return 0;
    }

    @Override
    public ICancellationSignal createCancellationSignal() throws RemoteException {
        return null;
    }

    @Override
    public Uri canonicalize(@NonNull AttributionSource attributionSource, Uri uri) throws RemoteException {
        return null;
    }

    @Override
    public void canonicalizeAsync(@NonNull AttributionSource attributionSource, Uri uri, RemoteCallback callback) throws RemoteException {

    }

    @Override
    public Uri uncanonicalize(@NonNull AttributionSource attributionSource, Uri uri) throws RemoteException {
        return null;
    }

    @Override
    public void uncanonicalizeAsync(@NonNull AttributionSource attributionSource, Uri uri, RemoteCallback callback) throws RemoteException {

    }

    @Override
    public boolean refresh(@NonNull AttributionSource attributionSource, Uri url, @Nullable Bundle extras, ICancellationSignal cancellationSignal) throws RemoteException {
        return false;
    }

    @Override
    public String[] getStreamTypes(AttributionSource attributionSource, Uri url, String mimeTypeFilter) throws RemoteException {
        return new String[0];
    }

    @Override
    public AssetFileDescriptor openTypedAssetFile(@NonNull AttributionSource attributionSource, Uri url, String mimeType, Bundle opts, ICancellationSignal signal) throws RemoteException, FileNotFoundException {
        return null;
    }

    private final Binder binderStub = new Binder();

    @Override
    public IBinder asBinder() {
        return binderStub;
    }
}
