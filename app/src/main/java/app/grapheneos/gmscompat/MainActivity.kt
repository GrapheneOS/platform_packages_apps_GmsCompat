package app.grapheneos.gmscompat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.grapheneos.gmscompat.Const.PLAY_SERVICES_PKG

val USAGE_GUIDE_URL = "https://grapheneos.org/usage#sandboxed-google-play"

class MainActivity : AppCompatActivity(R.layout.main_activity) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isPkgInstalled(PLAY_SERVICES_PKG)) {
            val uri = Uri.parse(USAGE_GUIDE_URL)
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            finishAndRemoveTask()
            return
        }
    }
}
