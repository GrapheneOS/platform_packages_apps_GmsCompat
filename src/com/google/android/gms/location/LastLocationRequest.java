package com.google.android.gms.location;

import android.os.Parcel;
import android.os.Parcelable;

import app.grapheneos.gmscompat.safeparcel.Property;
import app.grapheneos.gmscompat.safeparcel.SafeParcel;
import app.grapheneos.gmscompat.safeparcel.SpReadOnly;

// https://developers.google.com/android/reference/com/google/android/gms/location/LastLocationRequest
public class LastLocationRequest extends SpReadOnly {
    @Property(1) public long maxAge = Long.MAX_VALUE;
    @Property(2) public int granularity = LocationRequest.GRANULARITY_PERMISSION_LEVEL;
//    @Property(3) public boolean locationSettingsIgnored; // also named "bypass"
//    @Property(4) public String moduleId;
//    @Property(5) public ClientIdentity impersonation;

// SafeParcel code block generated with Spoon | START
    public static final Parcelable.Creator<LastLocationRequest> CREATOR = new Parcelable.Creator<LastLocationRequest>() {
        public LastLocationRequest createFromParcel(Parcel p) {
            LastLocationRequest o = new LastLocationRequest();
            final int objectEnd = SafeParcel.getObjectEnd(p);
            while (p.dataPosition() < objectEnd) {
                int ph = SafeParcel.propHeader(p);
                switch (SafeParcel.propId(ph)) {
                    case 1 :
                        o.maxAge = SafeParcel.readLong(ph, p);
                        continue;
                    case 2 :
                        o.granularity = SafeParcel.readInt(ph, p);
                        continue;
                    default :
                        SafeParcel.skipProp(ph, p);
                }
            }
            SafeParcel.checkFullRead(objectEnd, p);
            return o;
        }

        public LastLocationRequest[] newArray(int size) {
            return new LastLocationRequest[size];
        }
    };
// SafeParcel code block generated with Spoon | END
}
