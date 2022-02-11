package com.google.android.gms.location;

import android.os.Parcel;
import android.os.Parcelable;

import app.grapheneos.gmscompat.safeparcel.Property;
import app.grapheneos.gmscompat.safeparcel.SafeParcel;
import app.grapheneos.gmscompat.safeparcel.SpReadOnly;

// https://developers.google.com/android/reference/com/google/android/gms/location/LocationRequest
public class LocationRequest extends SpReadOnly {
    public static final int PRIORITY_BALANCED_POWER_ACCURACY = 102;
    public static final int PRIORITY_HIGH_ACCURACY = 100;
    public static final int PRIORITY_LOW_POWER = 104;
    public static final int PRIORITY_NO_POWER = 105;

    @Property(1) public int priority;
    @Property(2) public long interval;
    @Property(3) public long fastestInterval;
    @Property(4) public boolean explicitFastestInterval;
    @Property(5) public long expirationTime;
    @Property(6) public int numUpdates;
    @Property(7) public float smallestDesplacement;
    @Property(8) public long maxWaitTime;
    @Property(9) public boolean waitForAccurateLocation;

// SafeParcel code block generated with Spoon | START
    public static final Parcelable.Creator<LocationRequest> CREATOR = new Parcelable.Creator<LocationRequest>() {
        public LocationRequest createFromParcel(Parcel p) {
            LocationRequest o = new LocationRequest();
            final int objectEnd = SafeParcel.getObjectEnd(p);
            while (p.dataPosition() < objectEnd) {
                int ph = SafeParcel.propHeader(p);
                switch (SafeParcel.propId(ph)) {
                    case 1 :
                        o.priority = SafeParcel.readInt(ph, p);
                        continue;
                    case 2 :
                        o.interval = SafeParcel.readLong(ph, p);
                        continue;
                    case 3 :
                        o.fastestInterval = SafeParcel.readLong(ph, p);
                        continue;
                    case 4 :
                        o.explicitFastestInterval = SafeParcel.readBoolean(ph, p);
                        continue;
                    case 5 :
                        o.expirationTime = SafeParcel.readLong(ph, p);
                        continue;
                    case 6 :
                        o.numUpdates = SafeParcel.readInt(ph, p);
                        continue;
                    case 7 :
                        o.smallestDesplacement = SafeParcel.readFloat(ph, p);
                        continue;
                    case 8 :
                        o.maxWaitTime = SafeParcel.readLong(ph, p);
                        continue;
                    case 9 :
                        o.waitForAccurateLocation = SafeParcel.readBoolean(ph, p);
                        continue;
                    default :
                        SafeParcel.skipProp(ph, p);
                }
            } 
            SafeParcel.checkFullRead(objectEnd, p);
            return o;
        }
    
        public LocationRequest[] newArray(int size) {
            return new LocationRequest[size];
        }
    };
// SafeParcel code block generated with Spoon | END
}
