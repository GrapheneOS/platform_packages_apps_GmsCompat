package app.grapheneos.gmscompat;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import java.lang.ref.WeakReference;

public class App extends Application {
    private static WeakReference<Context> ctx;
    private static SharedPreferences preferences;

    public void onCreate() {
        super.onCreate();
        ctx = new WeakReference<Context>(getApplicationContext());
    }

    public static Context ctx() {
        return ctx.get();
    }

    public static SharedPreferences preferences() {
        return preferences;
    }

    public interface NotificationChannels {
        String PERSISTENT_FG_SERVICE = "persistent_fg_service";
    }

    public interface NotificationIds {
        int PERSISTENT_FG_SERVICE = 1;
    }
}
