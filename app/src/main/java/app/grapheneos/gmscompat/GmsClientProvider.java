package app.grapheneos.gmscompat;

import android.os.Bundle;

public class GmsClientProvider extends AbsContentProvider {
    static final String KEY_RESULT = "result";
    private static final int METHOD_GET_REDIRECTABLE_INTERFACES = 0;
    private static final int METHOD_GET_REDIRECTOR = 1;

    static final String KEY_BINDER = "binder";
    static final String KEY_BINDER_TRANSACTION_CODES = "binder_txn_codes";

    public Bundle call(String methodStr, String arg, Bundle bundleArg) {
        int method = Integer.parseInt(methodStr);
        switch (method) {
            case METHOD_GET_REDIRECTABLE_INTERFACES:
                return Redirections.getInterfaces();
            case METHOD_GET_REDIRECTOR:
                return Redirections.getRedirector(Integer.parseInt(arg));
        }
        return null;
    }
}
