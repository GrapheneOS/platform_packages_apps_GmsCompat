package android.os;

/** Compile-only platform API signature. Never included in the test dex. */
public class BinderWrapper implements IBinder {
    public BinderWrapper(IBinder base) { throw new AssertionError(); }
    public boolean transact(int code, Parcel data, Parcel reply, int flags) throws RemoteException { throw new AssertionError(); }
    public String getInterfaceDescriptor() throws RemoteException { throw new AssertionError(); }
    public boolean pingBinder() { throw new AssertionError(); }
    public boolean isBinderAlive() { throw new AssertionError(); }
    public IInterface queryLocalInterface(String s) { throw new AssertionError(); }
    public void dump(java.io.FileDescriptor fd, String[] args) throws RemoteException { throw new AssertionError(); }
    public void dumpAsync(java.io.FileDescriptor fd, String[] args) throws RemoteException { throw new AssertionError(); }
    public void linkToDeath(DeathRecipient r, int flags) throws RemoteException { throw new AssertionError(); }
    public boolean unlinkToDeath(DeathRecipient r, int flags) { throw new AssertionError(); }
}
