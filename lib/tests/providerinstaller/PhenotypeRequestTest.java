package app.grapheneos.gmscompat.lib.providerinstaller;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;

/** Standalone device test: uses Android's real native Parcel and Binder implementation. */
public final class PhenotypeRequestTest {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    static Parcel request(int service, String pkg, boolean duplicate, IBinder callback, IBinder extra) {
        Parcel p = Parcel.obtain();
        p.writeInterfaceToken(PhenotypeRequest.DESCRIPTOR);
        p.writeStrongBinder(callback);
        p.writeInt(1);
        p.writeInt(0xffff4f45);
        int size = p.dataPosition(); p.writeInt(0);
        int body = p.dataPosition();
        p.writeInt(0x40002); p.writeInt(service);
        string(p, pkg);
        if (duplicate) string(p, pkg);
        p.writeInt(0xffff0063);
        int n = p.dataPosition(); p.writeInt(0);
        int b = p.dataPosition(); p.writeStrongBinder(extra);
        int e = p.dataPosition(); p.setDataPosition(n); p.writeInt(e-b); p.setDataPosition(e);
        e = p.dataPosition(); p.setDataPosition(size); p.writeInt(e-body); p.setDataPosition(e);
        p.writeInt(0x12345678); // trailing data must survive
        return p;
    }
    static void string(Parcel p, String s) {
        p.writeInt(0xffff0004); int n=p.dataPosition(); p.writeInt(0);
        int b=p.dataPosition(); p.writeString(s); int e=p.dataPosition();
        p.setDataPosition(n); p.writeInt(e-b); p.setDataPosition(e);
    }
    static void roundtrip(String host) {
        Binder callback = new Binder(), extra = new Binder();
        Parcel p=request(51, PhenotypeRequest.GMS, false, callback, extra);
        int saved=p.dataPosition();
        Parcel q=PhenotypeRequest.rewrite(p,host);
        check(q!=null); check(p.dataPosition()==saved);
        q.enforceInterface(PhenotypeRequest.DESCRIPTOR);
        check(q.readStrongBinder()==callback); check(q.readInt()==1);
        check(q.readInt()==0xffff4f45); int size=q.readInt(); int end=q.dataPosition()+size;
        check(q.readInt()==0x40002); check(q.readInt()==51);
        check(q.readInt()==0xffff0004); int length=q.readInt(); int b=q.dataPosition();
        check(host.equals(q.readString())); check(q.dataPosition()==b+length);
        check(q.readInt()==0xffff0063); q.readInt(); check(q.readStrongBinder()==extra);
        check(q.dataPosition()==end); check(q.readInt()==0x12345678); check(q.dataAvail()==0);
        q.recycle(); p.recycle();
    }
    public static void main(String[] args) {
        roundtrip("com.google.android.apps.walletnfcrel"); roundtrip("a.b");
        Parcel[] rejected={request(79,PhenotypeRequest.GMS,false,null,null),
            request(51,"already.correct",false,null,null),
            request(51,PhenotypeRequest.GMS,true,null,null),Parcel.obtain()};
        for(Parcel p:rejected) {int pos=p.dataPosition(); check(PhenotypeRequest.rewrite(p,"host")==null); check(p.dataPosition()==pos); p.recycle();}
        Parcel p=request(51,PhenotypeRequest.GMS,false,null,null);
        check(PhenotypeRequest.rewrite(p,PhenotypeRequest.GMS)==null);
        check(PhenotypeRequest.rewrite(p,null)==null);
        p.setDataPosition(0); p.enforceInterface(PhenotypeRequest.DESCRIPTOR); p.readStrongBinder(); p.readInt(); p.readInt();
        p.writeInt(Integer.MAX_VALUE);
        check(PhenotypeRequest.rewrite(p,"host")==null); p.recycle();
        System.out.println("PASS: " + checks + " assertions (native Android Parcel/Binder)");
    }
}
