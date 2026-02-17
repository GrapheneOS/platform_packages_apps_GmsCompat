package com.google.android.gms.constellation;

import android.app.appsearch.safeparcel.AbstractSafeParcelable;
import android.app.appsearch.safeparcel.SafeParcelable;
import android.os.Parcel;
import android.os.Parcelable;

@SafeParcelable.Class(creator = "VerifyPhoneNumberRequestCreator")
public class VerifyPhoneNumberRequest extends AbstractSafeParcelable {
    public static final Parcelable.Creator<VerifyPhoneNumberRequest> CREATOR =
            new VerifyPhoneNumberRequestCreator();

    @Field(id = 1)
    public final String policyId;

    @Constructor
    public VerifyPhoneNumberRequest(@Param(id = 1) String policyId) {
        this.policyId = policyId;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        VerifyPhoneNumberRequestCreator.writeToParcel(this, dest, flags);
    }
}