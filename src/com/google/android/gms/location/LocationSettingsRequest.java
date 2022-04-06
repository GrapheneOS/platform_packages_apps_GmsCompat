package com.google.android.gms.location;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

import app.grapheneos.gmscompat.safeparcel.Property;
import app.grapheneos.gmscompat.safeparcel.SafeParcel;
import app.grapheneos.gmscompat.safeparcel.SpReadOnly;

// https://developers.google.com/android/reference/com/google/android/gms/location/LocationSettingsRequest.Builder
public class LocationSettingsRequest extends SpReadOnly {
    @Property(1) public List<LocationRequest> requests;
    @Property(2) public boolean alwaysShow;
    @Property(3) public boolean needBle;

// SafeParcel code block generated with Spoon | START
    public static final Parcelable.Creator<LocationSettingsRequest> CREATOR = new Parcelable.Creator<LocationSettingsRequest>() {
        public LocationSettingsRequest createFromParcel(Parcel p) {
            LocationSettingsRequest o = new LocationSettingsRequest();
            final int objectEnd = SafeParcel.getObjectEnd(p);
            while (p.dataPosition() < objectEnd) {
                int ph = SafeParcel.propHeader(p);
                switch (SafeParcel.propId(ph)) {
                    case 1 :
                        o.requests = SafeParcel.readParcelableList(ph, p, LocationRequest.CREATOR);
                        continue;
                    case 2 :
                        o.alwaysShow = SafeParcel.readBoolean(ph, p);
                        continue;
                    case 3 :
                        o.needBle = SafeParcel.readBoolean(ph, p);
                        continue;
                    default :
                        SafeParcel.skipProp(ph, p);
                }
            } 
            SafeParcel.checkFullRead(objectEnd, p);
            return o;
        }
    
        public LocationSettingsRequest[] newArray(int size) {
            return new LocationSettingsRequest[size];
        }
    };
// SafeParcel code block generated with Spoon | END
}
