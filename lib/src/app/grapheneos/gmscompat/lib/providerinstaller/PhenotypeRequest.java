package app.grapheneos.gmscompat.lib.providerinstaller;

import android.os.Parcel;

/** Narrow GetServiceRequest rewrite; never changes Binder's authenticated caller. */
public final class PhenotypeRequest {
    public static final String DESCRIPTOR = "com.google.android.gms.common.internal.IGmsServiceBroker";
    static final String GMS = "com.google.android.gms";

    /** Returns an owned replacement, or null to forward the original unchanged. */
    public static Parcel rewrite(Parcel data, String hostPackage) {
        int saved = data.dataPosition();
        Parcel out = null;
        try {
            data.setDataPosition(0);
            data.enforceInterface(DESCRIPTOR);
            data.readStrongBinder(); // callback: preserved by appendFrom, never marshalled
            if (data.readInt() != 1) return null;
            // Only support the extended SafeParcelable object header used by GetServiceRequest.
            if (data.readInt() != 0xffff4f45) return null;
            int sizeOffset = data.dataPosition();
            int size = data.readInt();
            int body = data.dataPosition();
            if (size < 0 || size > data.dataSize() - body) return null;
            int end = body + size;
            int service = -1, fieldStart = -1, fieldEnd = -1;
            boolean seenService = false;
            while (data.dataPosition() < end) {
                int start = data.dataPosition();
                if (end - start < 4) return null;
                int header = data.readInt();
                int length = header >>> 16;
                if (length == 0xffff) {
                    if (end - data.dataPosition() < 4) return null;
                    length = data.readInt();
                }
                int value = data.dataPosition();
                if (length < 0 || length > end - value) return null;
                int next = value + length;
                switch (header & 0xffff) {
                    case 2: // service ID
                        if (seenService || length != 4) return null;
                        seenService = true;
                        service = data.readInt();
                        break;
                    case 4: // calling package
                        if (fieldStart != -1 || length < 4) return null;
                        String pkg = data.readString();
                        if (data.dataPosition() != next || !GMS.equals(pkg)) return null;
                        fieldStart = start;
                        fieldEnd = next;
                        break;
                    default:
                        break; // preserve unknown fields, including Binder objects, byte-for-byte
                }
                data.setDataPosition(next);
            }
            if (service != 51 || fieldStart < 0 || hostPackage == null
                    || hostPackage.isEmpty() || GMS.equals(hostPackage)) return null;
            out = Parcel.obtain();
            out.appendFrom(data, 0, fieldStart);
            out.setDataPosition(out.dataSize());
            out.writeInt(0xffff0004);
            int fieldSizeOffset = out.dataPosition();
            out.writeInt(0);
            int stringStart = out.dataPosition();
            out.writeString(hostPackage);
            int stringEnd = out.dataPosition();
            out.setDataPosition(fieldSizeOffset);
            out.writeInt(stringEnd - stringStart);
            out.setDataPosition(stringEnd);
            out.appendFrom(data, fieldEnd, data.dataSize() - fieldEnd);
            int delta = out.dataSize() - data.dataSize();
            out.setDataPosition(sizeOffset);
            out.writeInt(size + delta);
            out.setDataPosition(0);
            Parcel result = out;
            out = null;
            return result;
        } catch (RuntimeException malformed) {
            return null;
        } finally {
            data.setDataPosition(saved);
            if (out != null) out.recycle();
        }
    }

    private PhenotypeRequest() {}
}
