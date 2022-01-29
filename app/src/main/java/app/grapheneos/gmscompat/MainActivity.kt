package app.grapheneos.gmscompat

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.grapheneos.gmscompat.Constants.PLAY_SERVICES_PKG

val USAGE_GUIDE_URL = "https://grapheneos.org/usage#sandboxed-google-play"

class MainActivity : AppCompatActivity(R.layout.main_activity) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isPkgInstalled(this, PLAY_SERVICES_PKG)) {
            val uri = Uri.parse(USAGE_GUIDE_URL)
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            finishAndRemoveTask()
            return
        }
    }
}

fun isPkgInstalled(ctx: Context, pkg: String): Boolean {
    try {
        ctx.packageManager.getPackageInfo(pkg, 0)
        return true
    } catch (e: PackageManager.NameNotFoundException) {
        return false
    }
}
