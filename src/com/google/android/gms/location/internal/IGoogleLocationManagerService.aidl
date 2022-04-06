package com.google.android.gms.location.internal;

import android.app.PendingIntent;
import android.location.Location;
import android.os.Bundle;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.internal.IFusedLocationProviderCallback;
import com.google.android.gms.location.internal.ISettingsCallbacks;
import com.google.android.gms.location.internal.LocationRequestInternal;
import com.google.android.gms.location.internal.LocationRequestUpdateData;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.ILocationListener;

interface IGoogleLocationManagerService {
    Location getLastLocation() = 6;
    Location getLastLocation2(String packageName) = 20;
    Location getLastLocation3(String contextAttributionTag) = 79;

    LocationAvailability getLocationAvailability(String packageName) = 33;
    void requestLocationSettingsDialog(in LocationSettingsRequest settingsRequest, ISettingsCallbacks callback, String packageName) = 62;

    void updateLocationRequest(in LocationRequestUpdateData locationRequestUpdateData) = 58;
    void flushLocations(IFusedLocationProviderCallback callback) = 66;
}
