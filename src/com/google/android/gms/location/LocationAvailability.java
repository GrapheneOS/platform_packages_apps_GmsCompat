package com.google.android.gms.location;

import android.os.Parcel;
import android.os.Parcelable;

import app.grapheneos.gmscompat.safeparcel.Property;
import app.grapheneos.gmscompat.safeparcel.SafeParcel;
import app.grapheneos.gmscompat.safeparcel.SpWriteOnly;

// https://developers.google.com/android/reference/com/google/android/gms/location/LocationAvailability
public class LocationAvailability extends SpWriteOnly {

    /*
    @Deprecated
    @Property(1) public int cellStatus;
    @Deprecated
    @Property(2) public int wifiStatus;
    @Property(3) public long elapsedRealtimeNs;
     */
    @Property(4) public int locationStatus;
    // @Deprecated @Property(5) public NetworkLocationStatus[]

    private static final int STATUS_AVAILABLE = 0;
    private static final int STATUS_UNAVAILABLE = 1000;

    private LocationAvailability(int status) {
        locationStatus = status;
    }

    private static final LocationAvailability AVAILABLE = new LocationAvailability(STATUS_AVAILABLE);
    private static final LocationAvailability UNAVAILABLE = new LocationAvailability(STATUS_UNAVAILABLE);

    public static LocationAvailability get(boolean available) {
        return available? AVAILABLE : UNAVAILABLE;
    }

    public boolean isLocationAvailable() {
        return locationStatus < STATUS_UNAVAILABLE;
    }

// SafeParcel code block generated with Spoon | START
    public void writeToParcel(Parcel p, int wtpFlags) {
        final int headerEnd = SafeParcel.beginObjectHeader(p);
        SafeParcel.writeInt(4, this.locationStatus, p);
        SafeParcel.completeObjectHeader(headerEnd, p);
    }
    
    public static final Parcelable.Creator<LocationAvailability> CREATOR = null;
// SafeParcel code block generated with Spoon | END
}
