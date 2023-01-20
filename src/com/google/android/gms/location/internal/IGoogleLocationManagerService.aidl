package com.google.android.gms.location.internal;

import android.app.PendingIntent;
import android.location.Location;
import android.os.Bundle;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.api.internal.IStatusCallback;
import com.google.android.gms.common.internal.ICancelToken;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.LastLocationRequest;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationAvailabilityRequest;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.ILocationListener;
import com.google.android.gms.location.internal.IFusedLocationProviderCallback;
import com.google.android.gms.location.internal.ILocationStatusCallback;
import com.google.android.gms.location.internal.ISettingsCallbacks;
import com.google.android.gms.location.internal.LocationReceiver;
import com.google.android.gms.location.internal.LocationRequestInternal;
import com.google.android.gms.location.internal.LocationRequestUpdateData;

interface IGoogleLocationManagerService {
    Location getLastLocation() = 6;
    Location getLastLocation2(String packageName) = 20;
    Location getLastLocation3(String contextAttributionTag) = 79;
    void getLastLocation4(in LastLocationRequest request, ILocationStatusCallback callback) = 81;
    void getLastLocation5(in LastLocationRequest request, in LocationReceiver receiver) = 89;

    LocationAvailability getLocationAvailability(String packageName) = 33;
    void getLocationAvailability2(in LocationAvailabilityRequest request, in LocationReceiver receiver) = 90;

    void requestLocationSettingsDialog(in LocationSettingsRequest settingsRequest, ISettingsCallbacks callback, String packageName) = 62;

    void updateLocationRequest(in LocationRequestUpdateData locationRequestUpdateData) = 58;
    void flushLocations(IFusedLocationProviderCallback callback) = 66;

    void registerLocationReceiver(in LocationReceiver receiver, in LocationRequest request, IStatusCallback callback) = 87;
    void unregisterLocationReceiver(in LocationReceiver receiver, IStatusCallback callback) = 88;

    ICancelToken getCurrentLocation(in CurrentLocationRequest request, ILocationStatusCallback callback) = 86;
    ICancelToken getCurrentLocation2(in CurrentLocationRequest request, in LocationReceiver receiver) = 91;
}
