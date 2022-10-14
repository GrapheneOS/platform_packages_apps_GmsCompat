package com.google.android.gms.location.internal;

import android.app.PendingIntent;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

import app.grapheneos.gmscompat.safeparcel.Property;
import app.grapheneos.gmscompat.safeparcel.SafeParcel;
import app.grapheneos.gmscompat.safeparcel.SpReadOnly;

public class LocationReceiver extends SpReadOnly {
    public static final int TYPE_ILocationListener = 1;
    public static final int TYPE_ILocationCallback = 2;
    public static final int TYPE_PendingIntent = 3;
    public static final int TYPE_ILocationStatusCallback = 4;
    public static final int TYPE_ILocationAvailabilityStatusCallback = 5;

    @Property(1) public int type;
//    @Property(2) public IBinder;
    @Property(3) public IBinder binder;
    @Property(4) public PendingIntent pendingIntent;
//    @Property(5) public String;
//    @Property(6) public String;

// SafeParcel code block generated with Spoon | START
    public static final Parcelable.Creator<LocationReceiver> CREATOR = new Parcelable.Creator<LocationReceiver>() {
        public LocationReceiver createFromParcel(Parcel p) {
            LocationReceiver o = new LocationReceiver();
            final int objectEnd = SafeParcel.getObjectEnd(p);
            while (p.dataPosition() < objectEnd) {
                int ph = SafeParcel.propHeader(p);
                switch (SafeParcel.propId(ph)) {
                    case 1 :
                        o.type = SafeParcel.readInt(ph, p);
                        continue;
                    case 3 :
                        o.binder = SafeParcel.readStrongBinder(ph, p);
                        continue;
                    case 4 :
                        o.pendingIntent = SafeParcel.readParcelable(ph, p, PendingIntent.CREATOR);
                        continue;
                    default :
                        SafeParcel.skipProp(ph, p);
                }
            }
            SafeParcel.checkFullRead(objectEnd, p);
            return o;
        }

        public LocationReceiver[] newArray(int size) {
            return new LocationReceiver[size];
        }
    };
// SafeParcel code block generated with Spoon | END
}
