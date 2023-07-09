package app.grapheneos.gmscompat;

import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

import com.android.internal.gmscompat.GmsCompatApp;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class App extends Application {
    private static Context ctx;
    private static Context deviceProtectedStorageContext;
    private static NotificationManager notificationManager;
    private static SharedPreferences preferences;
    private static Thread mainThread;
    private static Executor bgExecutor;

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
        bgExecutor = Executors.newSingleThreadExecutor();

        if (GmsCompatApp.PKG_NAME.equals(Application.getProcessName())) {
            // main process
            deviceProtectedStorageContext = ctx.createDeviceProtectedStorageContext();
            preferences = deviceProtectedStorageContext
                    .getSharedPreferences(MAIN_PROCESS_PREFS_FILE, MODE_PRIVATE);
            notificationManager = ctx.getSystemService(NotificationManager.class);
            Notifications.createNotificationChannels();

            new ConfigUpdateReceiver(ctx);
        }
    }

    public static Context ctx() {
        return ctx;
    }

    public static Context deviceProtectedStorageContext() {
        return deviceProtectedStorageContext;
    }

    public static NotificationManager notificationManager() {
        return notificationManager;
    }

    public static SharedPreferences preferences() {
        return preferences;
    }

    public static Thread mainThread() {
        return mainThread;
    }

    public static Executor bgExecutor() {
        return bgExecutor;
    }

    private static final String MAIN_PROCESS_PREFS_FILE = "prefs";

    public interface MainProcessPrefs {
        String LOCATION_REQUEST_REDIRECTION_ENABLED = "enabled_redirections"; // historical name

        String GmsCore_POWER_EXEMPTION_PROMPT_DISMISSED = "GmsCore_power_exemption_prompt_dismissed";
        String NOTIFICATION_DO_NOT_SHOW_AGAIN_PREFIX = "do_not_show_notification_";

        // set of package names of core GMS components that Play Store to allowed to update to unknown versions
        String GMS_PACKAGES_ALLOWED_TO_UPDATE_TO_UNKNOWN_VERSIONS = "gms_packages_allowed_to_update_to_unknown_versions";
    }
}
