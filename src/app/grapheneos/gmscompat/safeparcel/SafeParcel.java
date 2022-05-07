package app.grapheneos.gmscompat.safeparcel;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFormatException;
import android.os.Parcelable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class SafeParcel {
    public static int getObjectEnd(Parcel p) {
        int header = p.readInt();
        checkObjectHeader(header);
        int size = size(header, p);
        int pos = p.dataPosition();
        int end = pos + size;
        require(end >= pos && end <= p.dataSize());
        return end;
    }

    private static int size(int header, Parcel p) {
        int f = 0xffff_0000;
        if ((header & f) != f) {
            return header >>> 16;
        }
        return p.readInt();
    }

    private static final char OBJECT_START_MAGIC = 0x4f45;

    public static int beginObjectHeader(Parcel p) {
        return beginPropHeader(OBJECT_START_MAGIC, p);
    }

    public static void completeObjectHeader(int headerEnd, Parcel p) {
        completePropHeader(headerEnd, p);
    }

    private static void checkObjectHeader(int oheader) {
        //noinspection NumericCastThatLosesPrecision
        checkEq(OBJECT_START_MAGIC, (char) oheader);
    }

    public static void checkFullRead(int objEnd, Parcel p) {
        checkEq(p.dataPosition(), objEnd);
    }

    private static void writePropHeader(int size, int propId, Parcel p) {
        p.writeInt((size << 16) | propId);
    }

    public static int propHeader(Parcel p) {
        return p.readInt();
    }

    private static void writeNull(int propId, Parcel p) {
        p.writeInt(propId);
    }

    private static int beginPropHeader(int propId, Parcel p) {
        p.writeInt(0xffff_0000 | propId);
        p.writeInt(0);
        int propHeaderEnd = p.dataPosition();
        return propHeaderEnd;
    }

    private static void completePropHeader(int propHeaderEnd, Parcel p) {
        int propEnd = p.dataPosition();
        p.setDataPosition(propHeaderEnd - 4);
        int propLen = propEnd - propHeaderEnd;
        p.writeInt(propLen);
        p.setDataPosition(propEnd);
    }

    public static int propId(int ph) {
        //noinspection NumericCastThatLosesPrecision
        return (char) ph;
    }

    public static boolean readBoolean(int ph, Parcel p) {
        return readInt(ph, p) != 0;
    }

    public static void writeBoolean(int pi, boolean v, Parcel p) {
        writeInt(pi, v? 1 : 0, p);
    }

    public static byte readByte(int ph, Parcel p) {
        //noinspection NumericCastThatLosesPrecision
        return (byte) readInt(ph, p);
    }

    public static void writeByte(int pi, byte v, Parcel p) {
        writeInt(pi, v, p);
    }

    public static int readShort(int ph, Parcel p) {
        //noinspection NumericCastThatLosesPrecision
        return (short) readInt(ph, p);
    }

    public static void writeShort(int pi, short v, Parcel p) {
        writeInt(pi, v, p);
    }

    public static int readInt(int ph, Parcel p) {
        checkSize(4, ph, p);
        return p.readInt();
    }

    public static void writeInt(int pi, int v, Parcel p) {
        writePropHeader(4, pi, p);
        p.writeInt(v);
    }

    public static long readLong(int ph, Parcel p) {
        checkSize(8, ph, p);
        return p.readLong();
    }

    public static void writeLong(int pi, long v, Parcel p) {
        writePropHeader(8, pi, p);
        p.writeLong(v);
    }

    public static float readFloat(int ph, Parcel p) {
        checkSize(4, ph, p);
        return p.readFloat();
    }

    public static void writeFloat(int pi, float v, Parcel p) {
        writePropHeader(4, pi, p);
        p.writeFloat(v);
    }

    public static double readDouble(int ph, Parcel p) {
        checkSize(8, ph, p);
        return p.readDouble();
    }

    public static void writeDouble(int pi, double v, Parcel p) {
        writePropHeader(8, pi, p);
        p.writeDouble(v);
    }

    public static boolean[] readBooleanArray(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        int pos = p.dataPosition();
        boolean[] r = p.createBooleanArray();
        p.setDataPosition(pos + size);
        return r;
    }

    public static void writeBooleanArray(int pi, boolean[] v, Parcel p) {
        if (v != null) {
            int phe = beginPropHeader(pi, p);
            p.writeBooleanArray(v);
            completePropHeader(phe, p);
        } else {
            writeNull(pi, p);
        }
    }

    public static byte[] readByteArray(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        int pos = p.dataPosition();
        byte[] r = p.createByteArray();
        p.setDataPosition(pos + size);
        return r;
    }

    public static void writeByteArray(int pi, byte[] v, Parcel p) {
        if (v != null) {
            int phe = beginPropHeader(pi, p);
            p.writeByteArray(v);
            completePropHeader(phe, p);
        } else {
            writeNull(pi, p);
        }
    }

    public static int[] readIntArray(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        int pos = p.dataPosition();
        int[] r = p.createIntArray();
        p.setDataPosition(pos + size);
        return r;
    }

    public static void writeIntArray(int pi, int[] v, Parcel p) {
        if (v != null) {
            int phe = beginPropHeader(pi, p);
            p.writeIntArray(v);
            completePropHeader(phe, p);
        } else {
            writeNull(pi, p);
        }
    }

    public static long[] readLongArray(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        int pos = p.dataPosition();
        long[] r = p.createLongArray();
        p.setDataPosition(pos + size);
        return r;
    }

    public static void writeLongArray(int pi, long[] v, Parcel p) {
        if (v != null) {
            int phe = beginPropHeader(pi, p);
            p.writeLongArray(v);
            completePropHeader(phe, p);
        } else {
            writeNull(pi, p);
        }
    }

    public static float[] readFloatArray(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        int pos = p.dataPosition();
        float[] r = p.createFloatArray();
        p.setDataPosition(pos + size);
        return r;
    }

    public static void writeFloatArray(int pi, float[] v, Parcel p) {
        if (v != null) {
            int phe = beginPropHeader(pi, p);
            p.writeFloatArray(v);
            completePropHeader(phe, p);
        } else {
            writeNull(pi, p);
        }
    }

    public static double[] readDoubleArray(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        int pos = p.dataPosition();
        double[] r = p.createDoubleArray();
        p.setDataPosition(pos + size);
        return r;
    }

    public static void writeDoubleArray(int pi, double[] v, Parcel p) {
        if (v != null) {
            int phe = beginPropHeader(pi, p);
            p.writeDoubleArray(v);
            completePropHeader(phe, p);
        } else {
            writeNull(pi, p);
        }
    }

    public static String readString(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        int pos = p.dataPosition();
        String r = p.readString();
        p.setDataPosition(pos + size);
        return r;
    }

    public static void writeString(int pi, String v, Parcel p) {
        if (v != null) {
            int phe = beginPropHeader(pi, p);
            p.writeString(v);
            completePropHeader(phe, p);
        } else {
            writeNull(pi, p);
        }
    }

    public static String[] readStringArray(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        int pos = p.dataPosition();
        String[] r = p.createStringArray();
        p.setDataPosition(pos + size);
        return r;
    }

    public static void writeStringArray(int pi, String[] v, Parcel p) {
        if (v != null) {
            int phe = beginPropHeader(pi, p);
            p.writeStringArray(v);
            completePropHeader(phe, p);
        } else {
            writeNull(pi, p);
        }
    }

    public static byte[][] readByteArray2D(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        int pos = p.dataPosition();
        int len = p.readInt();
        byte[][] res = new byte[len][];
        for (int i = 0; i < len; ++i) {
            res[i] = p.createByteArray();
        }
        p.setDataPosition(pos + size);
        return res;
    }

    public static void writeByteArray2D(int pi, byte[][] v, Parcel p) {
        if (v != null) {
            int phe = beginPropHeader(pi, p);
            int len = v.length;
            p.writeInt(len);
            for (int i = 0; i < len; ++i) {
                p.writeByteArray(v[i]);
            }
            completePropHeader(phe, p);
        } else {
            writeNull(pi, p);
        }
    }

    public static Bundle readBundle(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        int pos = p.dataPosition();
        @SuppressLint("ParcelClassLoader")
        Bundle r = p.readBundle();
        p.setDataPosition(pos + size);
        return r;
    }

    public static void writeBundle(int pi, Bundle v, Parcel p) {
        if (v != null) {
            int phe = beginPropHeader(pi, p);
            p.writeBundle(v);
            completePropHeader(phe, p);
        } else {
            writeNull(pi, p);
        }
    }

    public static IBinder readStrongBinder(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        int pos = p.dataPosition();
        IBinder r = p.readStrongBinder();
        p.setDataPosition(pos + size);
        return r;
    }

    public static void writeStrongBinder(int pi, IBinder v, Parcel p) {
        if (v != null) {
            int phe = beginPropHeader(pi, p);
            p.writeStrongBinder(v);
            completePropHeader(phe, p);
        } else {
            writeNull(pi, p);
        }
    }

    public static Parcel readParcel(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        int pos = p.dataPosition();
        Parcel res = Parcel.obtain();
        res.appendFrom(p, pos, size);
        p.setDataPosition(pos + size);
        return res;
    }

    public static void writeParcel(int pi, Parcel v, Parcel p) {
        if (v != null) {
            int phe = beginPropHeader(pi, p);
            p.appendFrom(v, 0, v.dataSize());
            completePropHeader(phe, p);
        } else {
            writeNull(pi, p);
        }
    }

    public static Boolean readBooleanB(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        checkEq(size, 4);
        return Boolean.valueOf(p.readInt() != 0);
    }

    public static void writeBooleanB(int pi, Boolean v, Parcel p) {
        if (v != null) {
            writePropHeader(4, pi, p);
            p.writeInt(v.booleanValue()? 1 : 0);
        } else {
            writeNull(pi, p);
        }
    }

    public static Integer readIntegerB(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        checkEq(size, 4);
        return Integer.valueOf(p.readInt());
    }

    public static void writeIntegerB(int pi, Integer v, Parcel p) {
        if (v != null) {
            writePropHeader(4, pi, p);
            p.writeInt(v.intValue());
        } else {
            writeNull(pi, p);
        }
    }

    public static Long readLongB(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        checkEq(size, 8);
        return Long.valueOf(p.readLong());
    }

    public static void writeLongB(int pi, Long v, Parcel p) {
        if (v != null) {
            writePropHeader(8, pi, p);
            p.writeLong(v.longValue());
        } else {
            writeNull(pi, p);
        }
    }

    public static Float readFloatB(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        checkEq(size, 4);
        return Float.valueOf(p.readFloat());
    }

    public static void writeFloatB(int pi, Float v, Parcel p) {
        if (v != null) {
            writePropHeader(4, pi, p);
            p.writeFloat(v.floatValue());
        } else {
            writeNull(pi, p);
        }
    }

    public static Double readDoubleB(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        checkEq(size, 8);
        return Double.valueOf(p.readDouble());
    }

    public static void writeDoubleB(int pi, Double v, Parcel p) {
        if (v != null) {
            writePropHeader(8, pi, p);
            p.writeDouble(v.doubleValue());
        } else {
            writeNull(pi, p);
        }
    }

    public static BigInteger readBigInteger(int ph, Parcel p) {
        byte[] arr = readByteArray(ph, p);
        if (arr == null) {
            return null;
        }
        return new BigInteger(arr);
    }

    public static void writeBigInteger(int pi, BigInteger v, Parcel p) {
        if (v != null) {
            int phe = beginPropHeader(pi, p);
            p.writeByteArray(v.toByteArray());
            completePropHeader(phe, p);
        } else {
            writeNull(pi, p);
        }
    }

    public static BigDecimal readBigDecimal(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        int pos = p.dataPosition();
        byte[] arr = p.createByteArray();
        int scale = p.readInt();
        p.setDataPosition(pos + size);
        return new BigDecimal(new BigInteger(arr), scale);
    }

    public static void writeBigDecimal(int pi, BigDecimal v, Parcel p) {
        if (v != null) {
            int phe = beginPropHeader(pi, p);
            p.writeByteArray(v.unscaledValue().toByteArray());
            p.writeInt(v.scale());
            completePropHeader(phe, p);
        } else {
            writeNull(pi, p);
        }
    }

    public static <T> T readParcelable(int ph, Parcel p, Parcelable.Creator<T> creator) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        int pos = p.dataPosition();
        T r = creator.createFromParcel(p);
        p.setDataPosition(pos + size);
        return r;
    }

    public static void writeParcelable(int pi, Parcelable v, Parcel p, int flags) {
        if (v != null) {
            int phe = beginPropHeader(pi, p);
            v.writeToParcel(p, flags);
            completePropHeader(phe, p);
        } else {
            writeNull(pi, p);
        }
    }

    private static void writeParcelableInternal(Parcelable v, Parcel p, int flags) {
        int lenOff = p.dataPosition();
        p.writeInt(lenOff); // overwritten below
        int start = p.dataPosition();
        v.writeToParcel(p, flags);
        int end = p.dataPosition();
        p.setDataPosition(lenOff);
        p.writeInt(end - start);
        p.setDataPosition(end);
    }

    public static <T> T[] readParcelableArray(int ph, Parcel p, Parcelable.Creator<T> creator) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        int pos = p.dataPosition();
        T[] r = p.createTypedArray(creator);
        p.setDataPosition(pos + size);
        return r;
    }

    public static void writeParcelableArray(int pi, Parcelable[] v, Parcel p, int flags) {
        if (v != null) {
            int phe = beginPropHeader(pi, p);
            int len = v.length;
            p.writeInt(len);
            for (int i = 0; i < len; ++i) {
                Parcelable e = v[i];
                if (e == null) {
                    p.writeInt(0);
                } else {
                    writeParcelableInternal(e, p, flags);
                }
            }
            completePropHeader(phe, p);
        } else {
            writeNull(pi, p);
        }
    }

    public static ArrayList<Integer> readIntegerList(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        int pos = p.dataPosition();
        int len = p.readInt();
        ArrayList<Integer> list = new ArrayList<>(len);
        for (int i = 0; i < len; ++i) {
            list.add(Integer.valueOf(p.readInt()));
        }
        p.setDataPosition(pos + size);
        return list;
    }

    public static void writeIntegerList(int pi, List<Integer> v, Parcel p) {
        if (v != null) {
            int phe = beginPropHeader(pi, p);
            int size = v.size();
            p.writeInt(size);
            for (int i = 0; i < size; ++i) {
                p.writeInt(v.get(i).intValue());
            }
            completePropHeader(phe, p);
        } else {
            writeNull(pi, p);
        }
    }

    public static ArrayList<Long> readLongList(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        int pos = p.dataPosition();
        int len = p.readInt();
        ArrayList<Long> list = new ArrayList<>(len);
        for (int i = 0; i < len; ++i) {
            list.add(Long.valueOf(p.readLong()));
        }
        p.setDataPosition(pos + size);
        return list;
    }

    public static void writeLongList(int pi, List<Long> v, Parcel p) {
        if (v != null) {
            int phe = beginPropHeader(pi, p);
            int size = v.size();
            p.writeInt(size);
            for (int i = 0; i < size; ++i) {
                p.writeLong(v.get(i).longValue());
            }
            completePropHeader(phe, p);
        } else {
            writeNull(pi, p);
        }
    }

    public static ArrayList<Double> readDoubleList(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        int pos = p.dataPosition();
        int len = p.readInt();
        ArrayList<Double> list = new ArrayList<>(len);
        for (int i = 0; i < len; ++i) {
            list.add(Double.valueOf(p.readDouble()));
        }
        p.setDataPosition(pos + size);
        return list;
    }

    public static void writeDoubleList(int pi, List<Double> v, Parcel p) {
        if (v != null) {
            int phe = beginPropHeader(pi, p);
            int size = v.size();
            p.writeInt(size);
            for (int i = 0; i < size; ++i) {
                p.writeDouble(v.get(i).doubleValue());
            }
            completePropHeader(phe, p);
        } else {
            writeNull(pi, p);
        }
    }

    public static ArrayList<String> readStringList(int ph, Parcel p) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        int pos = p.dataPosition();
        ArrayList<String> r = p.createStringArrayList();
        p.setDataPosition(pos + size);
        return r;
    }

    public static void writeStringList(int pi, List<String> v, Parcel p) {
        if (v != null) {
            int phe = beginPropHeader(pi, p);
            p.writeStringList(v);
            completePropHeader(phe, p);
        } else {
            writeNull(pi, p);
        }
    }

    public static <T> ArrayList<T> readParcelableList(int ph, Parcel p, Parcelable.Creator<T> creator) {
        int size = size(ph, p);
        if (size == 0) {
            return null;
        }
        int pos = p.dataPosition();
        ArrayList<T> res = p.createTypedArrayList(creator);
        p.setDataPosition(pos + size);
        return res;
    }

    public static <T extends Parcelable> void writeParcelableInternal(int pi, List<T> v, Parcel p, int flags) {
        if (v != null) {
            int phe = beginPropHeader(pi, p);
            int size = v.size();
            p.writeInt(size);
            for (int i = 0; i < size; ++i) {
                Parcelable e = v.get(i);
                if (e == null) {
                    p.writeInt(0);
                } else {
                    writeParcelableInternal(e, p, flags);
                }
            }
            completePropHeader(phe, p);
        } else {
            writeNull(pi, p);
        }
    }

    public static void skipProp(int ph, Parcel p) {
        int propSize = size(ph, p);
        p.setDataPosition(p.dataPosition() + propSize);
    }

    private static void checkSize(int expected, int ph, Parcel p) {
        int actual = size(ph, p);
        if (expected != actual) {
            throw new ParcelFormatException("expected: " + expected + " actual: " + actual);
        }
    }

    private static void checkEq(int a, int b) {
        if (a != b) {
            throw new ParcelFormatException("a: " + a + " b: " + b);
        }
    }

    private static void require(boolean v) {
        if (!v) {
            throw new ParcelFormatException();
        }
    }
}
