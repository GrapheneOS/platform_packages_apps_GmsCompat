package app.grapheneos.gmscompat;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.BroadcastReceiver;
 
public class App extends Application {
    private static Context ctx;
    private static SharedPreferences preferences;
    private static Thread mainThread;

    public void onCreate() {
        super.onCreate();
        maybeInit(this);

        // Needed to handle scenario where the user adds or removes GMS or GSF
        AppStateControl.enableOrDisableEsim(this);
        IntentFilter appsChangesFilter = new IntentFilter();
        appsChangesFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        appsChangesFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        appsChangesFilter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        appsChangesFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        appsChangesFilter.addDataScheme("package");
        registerReceiver(appChangesReceiver, appsChangesFilter);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        unregisterReceiver(appChangesReceiver);
    }

    private final BroadcastReceiver appChangesReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (context != null){
                AppStateControl.enableOrDisableEsim(context);
            }
        }
    };

    static void maybeInit(Context componentContext) {
        if (ctx != null) {
            return;
        }
        ctx = componentContext.getApplicationContext();
        mainThread = Thread.currentThread();

        if (Const.PKG_NAME.equals(Application.getProcessName())) {
            // main process
            preferences = ctx
                .createDeviceProtectedStorageContext()
                .getSharedPreferences(MAIN_PROCESS_PREFS_FILE, MODE_PRIVATE);
            Redirections.init(preferences);
        }
    }

    public static Context ctx() {
        return ctx;
    }

    public static SharedPreferences preferences() {
        UtilsKt.mainProcess();
        return preferences;
    }

    public static Thread mainThread() {
        return mainThread;
    }

    public interface NotificationChannels {
        String PERSISTENT_FG_SERVICE = "persistent_fg_service";
    }

    public interface NotificationIds {
        int PERSISTENT_FG_SERVICE = 1;
    }

    private static final String MAIN_PROCESS_PREFS_FILE = "prefs";

    public interface MainProcessPrefs {
        String ENABLED_REDIRECTIONS = "enabled_redirections";
        String INITED_REDIRECTIONS = "inited_redirections";
    }
}
