package com.google.android.gms.location.internal;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.common.api.Status;

import app.grapheneos.gmscompat.safeparcel.Property;
import app.grapheneos.gmscompat.safeparcel.SafeParcel;
import app.grapheneos.gmscompat.safeparcel.SpWriteOnly;

public class FusedLocationProviderResult extends SpWriteOnly {
    public static final FusedLocationProviderResult SUCCESS = new FusedLocationProviderResult(Status.SUCCESS);
//    public static final FusedLocationProviderResult ERROR = new FusedLocationProviderResult(new Status(CommonStatusCodes.ERROR));

    @Property(1) public Status status;

    public FusedLocationProviderResult(Status status) {
        this.status = status;
    }

// SafeParcel code block generated with Spoon | START
    public void writeToParcel(Parcel p, int wtpFlags) {
        final int headerEnd = SafeParcel.beginObjectHeader(p);
        SafeParcel.writeParcelable(1, this.status, p, 0);
        SafeParcel.completeObjectHeader(headerEnd, p);
    }

    public static final Parcelable.Creator<FusedLocationProviderResult> CREATOR = null;
// SafeParcel code block generated with Spoon | END
}
