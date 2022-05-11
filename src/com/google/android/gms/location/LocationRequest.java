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
    @Property(3) public long minUpdateIntervalMillis;
    @Property(5) public long expirationTime = Long.MAX_VALUE;
    @Property(6) public int maxUpdates;
    @Property(7) public float minUpdateDistanceMeters;
    @Property(8) public long maxUpdateDelayMillis;
    @Property(9) public boolean waitForAccurateLocation;
    @Property(10) public long durationMillis = Long.MAX_VALUE;

    /*
    unused for now:

    @Property(11) public long maxUpdateAgeMillis = -1L;
    @Property(12) public int granularity;
    @Property(13) public int throttleBehavior;
    @Property(14) public String moduleId;
    @Property(15) public boolean bypass;
    @Property(16) public WorkSource workSource;
     */

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
                        o.minUpdateIntervalMillis = SafeParcel.readLong(ph, p);
                        continue;
                    case 5 :
                        o.expirationTime = SafeParcel.readLong(ph, p);
                        continue;
                    case 6 :
                        o.maxUpdates = SafeParcel.readInt(ph, p);
                        continue;
                    case 7 :
                        o.minUpdateDistanceMeters = SafeParcel.readFloat(ph, p);
                        continue;
                    case 8 :
                        o.maxUpdateDelayMillis = SafeParcel.readLong(ph, p);
                        continue;
                    case 9 :
                        o.waitForAccurateLocation = SafeParcel.readBoolean(ph, p);
                        continue;
                    case 10 :
                        o.durationMillis = SafeParcel.readLong(ph, p);
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
