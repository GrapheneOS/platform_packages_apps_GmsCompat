package com.google.android.gms.location;

import android.os.Parcel;
import android.os.Parcelable;

import app.grapheneos.gmscompat.safeparcel.Property;
import app.grapheneos.gmscompat.safeparcel.SafeParcel;
import app.grapheneos.gmscompat.safeparcel.SpWriteOnly;

// https://developers.google.com/android/reference/com/google/android/gms/location/LocationSettingsStates
public class LocationSettingsStates extends SpWriteOnly {
    @Property(1) public boolean gpsUsable;
    @Property(2) public boolean networkLocationUsable;
    @Property(3) public boolean bleUsable;
    @Property(4) public boolean gpsPresent;
    @Property(5) public boolean networkLocationPresent;
    @Property(6) public boolean blePresent;

// SafeParcel code block generated with Spoon | START
    public void writeToParcel(Parcel p, int wtpFlags) {
        final int headerEnd = SafeParcel.beginObjectHeader(p);
        SafeParcel.writeBoolean(1, this.gpsUsable, p);
        SafeParcel.writeBoolean(2, this.networkLocationUsable, p);
        SafeParcel.writeBoolean(3, this.bleUsable, p);
        SafeParcel.writeBoolean(4, this.gpsPresent, p);
        SafeParcel.writeBoolean(5, this.networkLocationPresent, p);
        SafeParcel.writeBoolean(6, this.blePresent, p);
        SafeParcel.completeObjectHeader(headerEnd, p);
    }
    
    public static final Parcelable.Creator<LocationSettingsStates> CREATOR = null;
// SafeParcel code block generated with Spoon | END
}
