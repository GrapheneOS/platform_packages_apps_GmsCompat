package app.grapheneos.gmscompat.safeparcel;

public abstract class SpWriteOnly extends SafeParcelable {
    public int describeContents() {
        return 0;
    }
}
