package com.google.android.gms.location.internal;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.location.LocationRequest;

import app.grapheneos.gmscompat.safeparcel.Property;
import app.grapheneos.gmscompat.safeparcel.SafeParcel;
import app.grapheneos.gmscompat.safeparcel.SpReadOnly;

public class LocationRequestInternal extends SpReadOnly {
    @Property(1) public LocationRequest request;

    /*
    these fields are never set by the recent GMS client versions

    @Property(5)  public List<ClientIdentity> clients;
    @Property(6)  public String tag;
    @Property(7)  public boolean hideAppOps;
    @Property(8)  public boolean forceCoarseLocation;
    @Property(9)  public boolean exemptFromBackgroundThrottle;
    @Property(10) public String moduleId;
    @Property(11) public boolean locationSettingsIgnored;
     */

    @Property(13) public String contextAttributionTag;
    /*
    these fields aren't necessary

    @Property(12) public boolean inaccurateLocationsDelayed;
    @Property(14) public long maxLocationAgeMillis;
    */

// SafeParcel code block generated with Spoon | START
    public static final Parcelable.Creator<LocationRequestInternal> CREATOR = new Parcelable.Creator<LocationRequestInternal>() {
        public LocationRequestInternal createFromParcel(Parcel p) {
            LocationRequestInternal o = new LocationRequestInternal();
            final int objectEnd = SafeParcel.getObjectEnd(p);
            while (p.dataPosition() < objectEnd) {
                int ph = SafeParcel.propHeader(p);
                switch (SafeParcel.propId(ph)) {
                    case 1 :
                        o.request = SafeParcel.readParcelable(ph, p, LocationRequest.CREATOR);
                        continue;
                    case 13 :
                        o.contextAttributionTag = SafeParcel.readString(ph, p);
                        continue;
                    default :
                        SafeParcel.skipProp(ph, p);
                }
            }
            SafeParcel.checkFullRead(objectEnd, p);
            return o;
        }

        public LocationRequestInternal[] newArray(int size) {
            return new LocationRequestInternal[size];
        }
    };
// SafeParcel code block generated with Spoon | END
}
