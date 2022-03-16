package app.grapheneos.gmscompat;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import static app.grapheneos.gmscompat.Const.GSF_PKG;
import static app.grapheneos.gmscompat.Const.PLAY_SERVICES_PKG;
import static app.grapheneos.gmscompat.Const.PIXEL_ESIM;
import static app.grapheneos.gmscompat.Const.PLAY_ESIM;

class AppStateControl {
    private static final String TAG = "AppStateControl";

    // If any of the Google apps required for eSIM support are disabled or missing, 
    // ask to disable eSIM support apps
    private static boolean shouldDisableEsim() {
        if (!UtilsKt.isPkgInstalled(GSF_PKG) || !UtilsKt.isPkgInstalled(PLAY_SERVICES_PKG)) {
            Log.d(TAG, "AppStateControl is going to disable eSIM support");
            return true;
        }
        Log.d(TAG, "AppStateControl is going to enable eSIM support");
        return false;
    }

    // If the required Google apps are disabled or missing, disable eSIM support apps,
    // otherwise enable them
    public static void enableOrDisableEsim(Context context) {
        PackageManager pm = context.getPackageManager();
        boolean disable = shouldDisableEsim();
        int flag = disable
            ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        pm.setApplicationEnabledSetting(PIXEL_ESIM, flag, 0);
        pm.setApplicationEnabledSetting(PLAY_ESIM, flag, 0);
    }
}
