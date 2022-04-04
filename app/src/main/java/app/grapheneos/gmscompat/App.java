package app.grapheneos.gmscompat;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

import com.android.internal.gmscompat.GmsCompatApp;

import java.util.ArrayList;

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
            setupNotificationChannels(ctx);
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

    public interface NotificationChannels {
        String PERSISTENT_FG_SERVICE = "persistent_fg_service";
        String PLAY_STORE_PENDING_USER_ACTION = "play_store_pending_user_action";
    }

    public interface NotificationIds {
        int PERSISTENT_FG_SERVICE = 1;
        int PLAY_STORE_PENDING_USER_ACTION = 2;
    }

    private static void setupNotificationChannels(Context ctx) {
        ArrayList<NotificationChannel> list = new ArrayList<>();
        String[] ids = { NotificationChannels.PERSISTENT_FG_SERVICE, NotificationChannels.PLAY_STORE_PENDING_USER_ACTION };
        int[] titles = { R.string.persistent_fg_service_notif, R.string.play_store_pending_user_action_notif };

        for (int i = 0; i < ids.length; ++i) {
            NotificationChannel nc = new NotificationChannel(ids[i], ctx.getText(titles[i]), NotificationManager.IMPORTANCE_LOW);
            nc.setShowBadge(false);
            list.add(nc);
        }
        notificationManager = ctx.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannels(list);
    }

    private static final String MAIN_PROCESS_PREFS_FILE = "prefs";

    public interface MainProcessPrefs {
        String ENABLED_REDIRECTIONS = "enabled_redirections";
        String INITED_REDIRECTIONS = "inited_redirections";
    }
}
