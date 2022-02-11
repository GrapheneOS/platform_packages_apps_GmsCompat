package com.google.android.gms.common.api;

import android.os.Parcel;
import android.os.Parcelable;

import app.grapheneos.gmscompat.safeparcel.Property;
import app.grapheneos.gmscompat.safeparcel.SafeParcel;
import app.grapheneos.gmscompat.safeparcel.SpWriteOnly;

// https://developers.google.com/android/reference/com/google/android/gms/common/api/Status
public final class Status extends SpWriteOnly implements Result {
//    public static final Status INTERNAL_ERROR = new Status(CommonStatusCodes.INTERNAL_ERROR);
//    public static final Status CANCELED = new Status(CommonStatusCodes.CANCELED);
    public static final Status SUCCESS = new Status(CommonStatusCodes.SUCCESS);

    @Property(1) public int statusCode;
//    @Property(2) public String statusMessage;
//    @Property(3) public PendingIntent resolution;

    public Status(int statusCode) {
        this.statusCode = statusCode;
    }

    @Override
    public Status getStatus() {
        return this;
    }

    public boolean isSuccess() {
        // there is SUCCESS (0) and SUCCESS_CACHE (-1)
        return statusCode <= CommonStatusCodes.SUCCESS;
    }

// SafeParcel code block generated with Spoon | START
    public void writeToParcel(Parcel p, int wtpFlags) {
        final int headerEnd = SafeParcel.beginObjectHeader(p);
        SafeParcel.writeInt(1, this.statusCode, p);
        SafeParcel.completeObjectHeader(headerEnd, p);
    }
    
    public static final Parcelable.Creator<Status> CREATOR = null;
// SafeParcel code block generated with Spoon | END
}
