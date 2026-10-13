package app.grapheneos.gmscompat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.android.internal.gmscompat.GmsCompatApp;

import static android.os.PatternMatcher.PATTERN_LITERAL;
import static app.grapheneos.gmscompat.Const.IS_DEV_BUILD;

public class ConfigUpdateReceiver extends BroadcastReceiver {
    public static final String CONFIG_HOLDER_PACKAGE = "app.grapheneos.gmscompat.config";
    public static final String CONFIG_HOLDER_PACKAGE_DEV = CONFIG_HOLDER_PACKAGE + ".dev";

    public ConfigUpdateReceiver(Context ctx) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        filter.addDataSchemeSpecificPart(CONFIG_HOLDER_PACKAGE, PATTERN_LITERAL);

        if (IS_DEV_BUILD) {
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addDataSchemeSpecificPart(CONFIG_HOLDER_PACKAGE_DEV, PATTERN_LITERAL);
        }

        ctx.registerReceiver(this, filter);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("ConfigUpdateReceiver", intent + " | uri: " + intent.getData());
        BinderGms2Gca.INSTANCE.updateConfig();
        var broadcast = new Intent(GmsCompatApp.CONFIG_UPDATED_BROADCAST);
        App.ctx().sendBroadcast(broadcast, GmsCompatApp.CLIENT_PERMISSION);
    }
}
