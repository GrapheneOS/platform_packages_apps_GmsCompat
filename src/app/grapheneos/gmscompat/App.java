package app.grapheneos.gmscompat;

import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

import com.android.internal.gmscompat.GmsCompatApp;

public class App extends Application {
    private static Context ctx;
    private static NotificationManager notificationManager;
    private static SharedPreferences preferences;
    private static Thread mainThread;

    public void onCreate() {
        super.onCreate();
        maybeInit(this);
    }

    static void maybeInit(Context componentContext) {
        if (ctx != null) {
            return;
        }
        ctx = componentContext.getApplicationContext();
        mainThread = Thread.currentThread();

        if (GmsCompatApp.PKG_NAME.equals(Application.getProcessName())) {
            // main process
            preferences = ctx
                .createDeviceProtectedStorageContext()
                .getSharedPreferences(MAIN_PROCESS_PREFS_FILE, MODE_PRIVATE);
            Redirections.init(preferences);

            notificationManager = ctx.getSystemService(NotificationManager.class);
            Notifications.createNotificationChannels();
        }
    }

    public static Context ctx() {
        return ctx;
    }

    public static NotificationManager notificationManager() {
        return notificationManager;
    }

    public static SharedPreferences preferences() {
        UtilsKt.mainProcess();
        return preferences;
    }

    public static Thread mainThread() {
        return mainThread;
    }

    private static final String MAIN_PROCESS_PREFS_FILE = "prefs";

    public interface MainProcessPrefs {
        String ENABLED_REDIRECTIONS = "enabled_redirections";
        String INITED_REDIRECTIONS = "inited_redirections";
    }
}
