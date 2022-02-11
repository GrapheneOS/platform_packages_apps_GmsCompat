package com.google.android.gms.location.internal;

import android.app.PendingIntent;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.location.ILocationCallback;
import com.google.android.gms.location.ILocationListener;

import app.grapheneos.gmscompat.safeparcel.Property;
import app.grapheneos.gmscompat.safeparcel.SafeParcel;
import app.grapheneos.gmscompat.safeparcel.SpReadOnly;

public class LocationRequestUpdateData extends SpReadOnly {
    public static final int OP_REQUEST_UPDATES = 1;
    public static final int OP_REMOVE_UPDATES = 2;

    @Property(1) public int opCode;
    @Property(2) public LocationRequestInternal request;
    @Property(3) public ILocationListener listener;
    @Property(4) public PendingIntent pendingIntent;
    @Property(5) public ILocationCallback callback;
    @Property(6) public IFusedLocationProviderCallback fusedLocationProviderCallback;
    @Property(8) public String appOpsReasonMessage;

// SafeParcel code block generated with Spoon | START
    public static final Parcelable.Creator<LocationRequestUpdateData> CREATOR = new Parcelable.Creator<LocationRequestUpdateData>() {
        public LocationRequestUpdateData createFromParcel(Parcel p) {
            LocationRequestUpdateData o = new LocationRequestUpdateData();
            final int objectEnd = SafeParcel.getObjectEnd(p);
            while (p.dataPosition() < objectEnd) {
                int ph = SafeParcel.propHeader(p);
                switch (SafeParcel.propId(ph)) {
                    case 1 :
                        o.opCode = SafeParcel.readInt(ph, p);
                        continue;
                    case 2 :
                        o.request = SafeParcel.readParcelable(ph, p, LocationRequestInternal.CREATOR);
                        continue;
                    case 3 :
                        o.listener = ILocationListener.Stub.asInterface(SafeParcel.readStrongBinder(ph, p));
                        continue;
                    case 4 :
                        o.pendingIntent = SafeParcel.readParcelable(ph, p, PendingIntent.CREATOR);
                        continue;
                    case 5 :
                        o.callback = ILocationCallback.Stub.asInterface(SafeParcel.readStrongBinder(ph, p));
                        continue;
                    case 6 :
                        o.fusedLocationProviderCallback = IFusedLocationProviderCallback.Stub.asInterface(SafeParcel.readStrongBinder(ph, p));
                        continue;
                    case 8 :
                        o.appOpsReasonMessage = SafeParcel.readString(ph, p);
                        continue;
                    default :
                        SafeParcel.skipProp(ph, p);
                }
            }
            SafeParcel.checkFullRead(objectEnd, p);
            return o;
        }

        public LocationRequestUpdateData[] newArray(int size) {
            return new LocationRequestUpdateData[size];
        }
    };
// SafeParcel code block generated with Spoon | END
}
