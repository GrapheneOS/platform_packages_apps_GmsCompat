package com.google.android.gms.location;

import android.os.Parcel;
import android.os.Parcelable;

import app.grapheneos.gmscompat.safeparcel.SafeParcel;
import app.grapheneos.gmscompat.safeparcel.SpReadOnly;

public class LocationAvailabilityRequest extends SpReadOnly {
//    @Property(1) public boolean bypass;
//    @Property(2) public ClientIdentity impersonation;

    public static final Parcelable.Creator<LocationAvailabilityRequest> CREATOR = new Creator<>() {
        public LocationAvailabilityRequest createFromParcel(Parcel p) {
            LocationAvailabilityRequest o = new LocationAvailabilityRequest();
            final int objectEnd = SafeParcel.getObjectEnd(p);
            while (p.dataPosition() < objectEnd) {
                int ph = SafeParcel.propHeader(p);
                SafeParcel.skipProp(ph, p);
            }
            SafeParcel.checkFullRead(objectEnd, p);
            return o;
        }

        public LocationAvailabilityRequest[] newArray(int size) {
            return new LocationAvailabilityRequest[size];
        }
    };
}
