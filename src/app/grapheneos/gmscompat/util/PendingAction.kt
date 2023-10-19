package app.grapheneos.gmscompat.util

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import app.grapheneos.gmscompat.App
import app.grapheneos.gmscompat.logd
import app.grapheneos.gmscompat.logw
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * An abstraction over PendingIntent that simplifies the task of executing an arbitrary function
 * when a PendingIntent is launched.
 *
 * Registered PendingAction turns into a no-op when the app process is restarted, unlike underlying
 * PendingIntent (system_server saves registered PendingIntent between app launches in most cases
 * as long as the OS is running)
 */
class PendingAction private constructor(val action: () -> Unit, val oneShot: Boolean) : BroadcastReceiver()
{
    private val id: Int
    val pendingIntent: PendingIntent
    private var unregistered = false

    init {
        id = lock.withLock {
            // reusing free IDs ensure that previous PendingIntents that may have been left from
            // the previous app process are canceled before the limit on number of PendingIntents
            // is reached
            val id = usedIds.nextClearBit(0)

            if (id > MAX_ID) {
                throw IllegalStateException()
            }

            usedIds.set(id)

            return@withLock id
        }

        val intentAction = this.javaClass.getName() + "." + id

        val ctx = App.ctx()
        // CANCEL_CURRENT to make sure PendingActions from previous process launches are never launched
        // (same id can be used for a different PendingAction in the current process)
        val piFlags = PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        pendingIntent = PendingIntent.getBroadcast(ctx, 0, Intent(intentAction).setPackage(ctx.packageName), piFlags)

        ctx.registerReceiver(this, IntentFilter(intentAction), Context.RECEIVER_NOT_EXPORTED)

        logd{"registered $id"}
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        lock.withLock {
            if (unregistered) {
                logw{"attempt to execute unregistered PendingAction $id"}
                return
            }

            if (oneShot) {
                // simpler than FLAG_ONE_SHOT
                unregister()
            }
        }
        action()
    }

    fun unregister() {
        lock.withLock {
            if (unregistered) {
                logw{"PendingAction $id is already unregistered"}
                return
            }

            unregistered = true
            pendingIntent.cancel()
            App.ctx().unregisterReceiver(this)

            check(usedIds.get(id))
            usedIds.clear(id)
        }

        logd{"unregistered $id"}
    }

    companion object {
        val lock = ReentrantLock()

        // there's a limit of 2000 PendingIntents per UID as of Android 12, but better to not set it that high
        const val MAX_ID = 500

        private val usedIds: BitSet = BitSet(MAX_ID)

        // Call unregister() when this action is no longer needed.
        // If the action is performed exactly once, use addOneShot() instead, it'll make sure that
        // unregister() is called after PendingIntent is launched for the first time
        fun add(action: () -> Unit) = PendingAction(action, false)

        fun addOneShot(action: () -> Unit) = PendingAction(action, true)
    }
}
