package app.grapheneos.gmscompat.lib.providerinstaller;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.*;
import android.os.Process;

public final class ProviderInstallerCompatTest {
    static final String HOST="com.google.android.apps.walletnfcrel";
    static class Host extends ContextWrapper {
        String pkg=HOST; int uid=Process.myUid();
        Host() { super(null); }
        public String getPackageName() { return pkg; }
        public ApplicationInfo getApplicationInfo() { ApplicationInfo a=new ApplicationInfo(); a.uid=uid; return a; }
    }
    static class Connection implements ServiceConnection {
        IBinder binder; int disconnected, died, empty;
        public void onServiceConnected(ComponentName n, IBinder b) { binder=b; }
        public void onServiceDisconnected(ComponentName n) { disconnected++; }
        public void onBindingDied(ComponentName n) { died++; }
        public void onNullBinding(ComponentName n) { empty++; }
    }
    static void check(boolean b) { PhenotypeRequestTest.check(b); }
    public static void main(String[] args) throws Exception {
        PhenotypeRequestTest.main(args);
        Host host=new Host(); Connection c=new Connection();
        Intent intent=new Intent("com.google.android.gms.phenotype.service.START").setPackage(PhenotypeRequest.GMS);
        ServiceConnection w=ProviderInstallerCompat.maybeWrap(host,intent,Process.myUserHandle(),c);
        check(w!=null);
        ComponentName name=new ComponentName(PhenotypeRequest.GMS,"Test");
        Binder base=new Binder() {
            { attachInterface(null,PhenotypeRequest.DESCRIPTOR); }
            protected boolean onTransact(int code, Parcel p, Parcel reply, int flags) {
                if(code!=46) { check(code==47); return true; }
                p.enforceInterface(PhenotypeRequest.DESCRIPTOR); p.readStrongBinder(); p.readInt();
                p.readInt(); p.readInt(); p.readInt(); int service=p.readInt();
                p.readInt(); p.readInt(); String pkg=p.readString();
                check((service==51 ? HOST : PhenotypeRequest.GMS).equals(pkg));
                reply.writeNoException(); return true;
            }
        };
        w.onServiceConnected(name,base); check(c.binder!=base);
        for(int service:new int[]{51,79}) {
            Parcel p=PhenotypeRequestTest.request(service,PhenotypeRequest.GMS,false,null,null),r=Parcel.obtain();
            check(c.binder.transact(46,p,r,0)); r.readException(); p.recycle(); r.recycle();
        }
        Parcel p=Parcel.obtain(); check(c.binder.transact(47,p,null,0)); p.recycle();
        w.onServiceDisconnected(name); w.onBindingDied(name); w.onNullBinding(name);
        check(c.disconnected==1 && c.died==1 && c.empty==1);
        Binder wrong=new Binder(); w.onServiceConnected(name,wrong); check(c.binder==wrong);
        host.uid++; check(ProviderInstallerCompat.maybeWrap(host,intent,Process.myUserHandle(),c)==null); host.uid--;
        host.pkg=PhenotypeRequest.GMS; check(ProviderInstallerCompat.maybeWrap(host,intent,Process.myUserHandle(),c)==null); host.pkg=HOST;
        check(ProviderInstallerCompat.maybeWrap(host,intent,null,c)==null);
        intent.setAction("unrelated"); check(ProviderInstallerCompat.maybeWrap(host,intent,Process.myUserHandle(),c)==null);
        intent.setAction("com.google.android.gms.phenotype.service.START").setPackage("other");
        check(ProviderInstallerCompat.maybeWrap(host,intent,Process.myUserHandle(),c)==null);
        System.out.println("PASS: " + PhenotypeRequestTest.checks + " total assertions, real platform BinderWrapper");
    }
}
