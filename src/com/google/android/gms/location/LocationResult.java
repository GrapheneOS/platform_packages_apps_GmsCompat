package com.google.android.gms.location;

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

import app.grapheneos.gmscompat.safeparcel.Property;
import app.grapheneos.gmscompat.safeparcel.SafeParcel;
import app.grapheneos.gmscompat.safeparcel.SpWriteOnly;

// https://developers.google.com/android/reference/com/google/android/gms/location/LocationResult
public class LocationResult extends SpWriteOnly {
    @Property(value = 1) public final List<Location> locations;

    public LocationResult(List<Location> locations) {
        this.locations = locations;
    }

// SafeParcel code block generated with Spoon | START
    public void writeToParcel(Parcel p, int wtpFlags) {
        final int headerEnd = SafeParcel.beginObjectHeader(p);
        SafeParcel.writeParcelableList(1, this.locations, p, 0);
        SafeParcel.completeObjectHeader(headerEnd, p);
    }

    public static final Parcelable.Creator<LocationResult> CREATOR = null;
// SafeParcel code block generated with Spoon | END
}
