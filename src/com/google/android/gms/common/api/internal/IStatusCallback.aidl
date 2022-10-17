package com.google.android.gms.common.api.internal;

import android.location.Location;

import com.google.android.gms.common.api.Status;

interface IStatusCallback {
    void onCompletion(in Status status);
}
