package app.grapheneos.gmscompat

import android.Manifest
import android.app.compat.gms.GmsCompat
import android.content.Intent
import android.net.Uri
import android.util.ArrayMap
import com.android.internal.gmscompat.BinderRedirector
import com.android.internal.gmscompat.GmsInfo
import com.android.internal.gmscompat.IClientOfGmsCore2Gca
import com.android.internal.gmscompat.dynamite.server.IFileProxyService

object BinderClientOfGmsCore2Gca : IClientOfGmsCore2Gca.Stub() {

    val descriptorsOfNotableInterfaces = NotableInterface.values().map { it.descriptor }.sorted()
    val mapOfDescriptorsOfNotableInterfaces = ArrayMap<String, NotableInterface>(NotableInterface.values().size).apply {
        NotableInterface.values().forEach {
            put(it.descriptor, it)
        }
    }

    override fun getRedirectableInterfaces(outNotableInterfaces: MutableList<String>): Array<String> {
        outNotableInterfaces.addAll(descriptorsOfNotableInterfaces)
        return Redirections.getInterfaces()
    }

    override fun getBinderRedirector(id: Int): BinderRedirector = Redirections.getRedirector(id)

    override fun getDynamiteFileProxyService(): IFileProxyService = BinderGms2Gca.dynamiteFileProxyService!!

    override fun onNotableInterfaceAcquired(interfaceDescriptor: String) {
        mapOfDescriptorsOfNotableInterfaces[interfaceDescriptor]!!.onAcquiredByClient()
    }
}

enum class NotableInterface(val descriptor: String) {
    ExposureNotificationService("com.google.android.gms.nearby.exposurenotification.internal.INearbyExposureNotificationService"),
    GamesService("com.google.android.gms.games.internal.IGamesService"),
    ;

    fun onAcquiredByClient() {
        val ctx = App.ctx()
        when (this) {
            ExposureNotificationService -> {
                if (!gmsCoreHasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                    Notifications.configurationRequired(
                            Notifications.CH_MISSING_PERMISSION,
                            ctx.getText(R.string.missing_permission),
                            ctx.getText(R.string.missing_permission_nearby_exposurenotifications),
                            ctx.getText(R.string.open_settings),
                            gmsCoreSettings()
                    ).show(Notifications.ID_GMS_CORE_MISSING_NEARBY_DEVICES_PERMISSION)
                }
            }
            GamesService -> {
                val playGamesPkg = "com.google.android.play.games"
                if (!isPkgInstalled(playGamesPkg) && GmsCompat.isGmsApp(GmsInfo.PACKAGE_PLAY_STORE, ctx.userId)) {
                    val uri = Uri.parse("market://details?id=$playGamesPkg")
                    val resolution = Intent(Intent.ACTION_VIEW, uri)
                    resolution.setPackage(GmsInfo.PACKAGE_PLAY_STORE)
                    Notifications.configurationRequired(
                            Notifications.CH_MISSING_PLAY_GAMES_APP,
                            ctx.getText(R.string.missing_app),
                            ctx.getText(R.string.missing_play_games_app),
                            ctx.getText(R.string.install),
                            resolution
                    ).show(Notifications.ID_MISSING_PLAY_GAMES_APP)
                }
            }
        }
    }
}
