package com.google.android.gms.location;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.Status;

import app.grapheneos.gmscompat.safeparcel.Property;
import app.grapheneos.gmscompat.safeparcel.SafeParcel;
import app.grapheneos.gmscompat.safeparcel.SpWriteOnly;

// https://developers.google.com/android/reference/com/google/android/gms/location/LocationSettingsResult
public class LocationSettingsResult extends SpWriteOnly implements Result {
    @Property(1) public Status status;
    @Property(2) public LocationSettingsStates settings;

    @Override
    public Status getStatus() {
        return status;
    }

    public LocationSettingsResult(LocationSettingsStates settings, Status status) {
        this.settings = settings;
        this.status = status;
    }

// SafeParcel code block generated with Spoon | START
    public void writeToParcel(Parcel p, int wtpFlags) {
        final int headerEnd = SafeParcel.beginObjectHeader(p);
        SafeParcel.writeParcelable(1, this.status, p, 0);
        SafeParcel.writeParcelable(2, this.settings, p, 0);
        SafeParcel.completeObjectHeader(headerEnd, p);
    }
    
    public static final Parcelable.Creator<LocationSettingsResult> CREATOR = null;
// SafeParcel code block generated with Spoon | END
}
