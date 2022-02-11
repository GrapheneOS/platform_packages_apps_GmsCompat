package app.grapheneos.gmscompat;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

public class App extends Application {
    private static Context ctx;
    private static SharedPreferences preferences;
    private static Thread mainThread;

    public void onCreate() {
        super.onCreate();
        ctx = getApplicationContext();
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
