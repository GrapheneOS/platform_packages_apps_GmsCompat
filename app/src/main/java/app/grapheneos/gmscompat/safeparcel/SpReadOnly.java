package app.grapheneos.gmscompat.safeparcel;

import android.os.Parcel;

public abstract class SpReadOnly extends SafeParcelable {
    public final int describeContents() {
        throw new IllegalStateException("this is a read-only SafeParcelable");
    }

    public final void writeToParcel(Parcel dest, int flags) {
        throw new IllegalStateException("this is a read-only SafeParcelable");
    }
}
