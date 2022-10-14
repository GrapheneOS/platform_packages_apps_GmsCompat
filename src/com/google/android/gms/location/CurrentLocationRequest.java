package com.google.android.gms.location;

import android.os.Parcel;
import android.os.Parcelable;

import app.grapheneos.gmscompat.safeparcel.Property;
import app.grapheneos.gmscompat.safeparcel.SafeParcel;
import app.grapheneos.gmscompat.safeparcel.SpReadOnly;

// https://developers.google.com/android/reference/com/google/android/gms/location/CurrentLocationRequest
public class CurrentLocationRequest extends SpReadOnly {
    @Property(1) public long maxUpdateAgeMillis = 60_000L;
    @Property(2) public int granularity = LocationRequest.GRANULARITY_PERMISSION_LEVEL;
    @Property(3) public int priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;
    @Property(4) public long durationMillis = Long.MAX_VALUE;
//    @Property(5) public boolean bypass;
//    @Property(6) public WorkSource workSource;
//    @Property(7) public int throttleBehavior;
//    @Property(8) public String moduleId;
//    @Property(9) public ClientIdentity impersonation;

// SafeParcel code block generated with Spoon | START
    public static final Parcelable.Creator<CurrentLocationRequest> CREATOR = new Parcelable.Creator<CurrentLocationRequest>() {
        public CurrentLocationRequest createFromParcel(Parcel p) {
            CurrentLocationRequest o = new CurrentLocationRequest();
            final int objectEnd = SafeParcel.getObjectEnd(p);
            while (p.dataPosition() < objectEnd) {
                int ph = SafeParcel.propHeader(p);
                switch (SafeParcel.propId(ph)) {
                    case 1 :
                        o.maxUpdateAgeMillis = SafeParcel.readLong(ph, p);
                        continue;
                    case 2 :
                        o.granularity = SafeParcel.readInt(ph, p);
                        continue;
                    case 3 :
                        o.priority = SafeParcel.readInt(ph, p);
                        continue;
                    case 4 :
                        o.durationMillis = SafeParcel.readLong(ph, p);
                        continue;
                    default :
                        SafeParcel.skipProp(ph, p);
                }
            }
            SafeParcel.checkFullRead(objectEnd, p);
            return o;
        }

        public CurrentLocationRequest[] newArray(int size) {
            return new CurrentLocationRequest[size];
        }
    };
// SafeParcel code block generated with Spoon | END
}
