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
}
