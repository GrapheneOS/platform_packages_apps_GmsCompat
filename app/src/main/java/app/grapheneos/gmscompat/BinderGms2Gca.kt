package app.grapheneos.gmscompat

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.RemoteException
import android.util.ArraySet
import com.android.internal.gmscompat.GmsInfo
import com.android.internal.gmscompat.IGms2Gca
import com.android.internal.gmscompat.dynamite.server.IFileProxyService

object BinderGms2Gca : IGms2Gca.Stub() {
    private val boundProcesses = ArraySet<IBinder>(10)

    fun connect(pkg: String, processName: String, callerBinder: IBinder) {
        logd{"callingPkg $pkg processName $processName callingPid ${Binder.getCallingPid()}"}

        val deathRecipient = DeathRecipient(callerBinder)
        try {
            // important to add before linkToDeath() to avoid race with binderDied() callback
            addBoundProcess(callerBinder)
            callerBinder.linkToDeath(deathRecipient, 0)
        } catch (e: RemoteException) {
            logd{"binder already died: " + e}
            deathRecipient.binderDied()
            return
        }
        PersistentFgService.start(pkg)
    }

    class DeathRecipient(val binder: IBinder) : IBinder.DeathRecipient {
        override fun binderDied() {
            removeBoundProcess(binder)
        }
    }

    fun addBoundProcess(binder: IBinder) {
        synchronized(boundProcesses) {
            boundProcesses.add(binder)
        }
    }

    fun removeBoundProcess(binder: IBinder) {
        synchronized(boundProcesses) {
            boundProcesses.remove(binder)
            if (boundProcesses.size == 0) {
                val ctx = App.ctx()
                val i = Intent(ctx, PersistentFgService::class.java)
                if (ctx.stopService(i)) {
                    logd{"no bound processes, stopping PersistentFgService"}
                }
            }
        }
    }

    override fun connectGsf(processName: String, callerBinder: IBinder) {
        connect(GmsInfo.PACKAGE_GSF, processName, callerBinder)
    }

    @Volatile
    var dynamiteFileProxyService: IFileProxyService? = null

    override fun connectGmsCore(processName: String, callerBinder: IBinder, fileProxyService: IFileProxyService?) {
        if (fileProxyService != null) {
            dynamiteFileProxyService = fileProxyService
        }
        connect(GmsInfo.PACKAGE_GMS_CORE, processName, callerBinder)
    }

    override fun connectPlayStore(processName: String, callerBinder: IBinder) {
        connect(GmsInfo.PACKAGE_PLAY_STORE, processName, callerBinder)
    }
}
