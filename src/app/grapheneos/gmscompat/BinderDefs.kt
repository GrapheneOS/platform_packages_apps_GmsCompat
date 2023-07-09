package app.grapheneos.gmscompat

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.os.BinderDef
import android.os.Build.IS_DEBUGGABLE
import android.util.ArrayMap
import android.util.ArraySet
import androidx.core.content.edit
import app.grapheneos.gmscompat.App.MainProcessPrefs
import app.grapheneos.gmscompat.location.GLocationService
import com.android.internal.gmscompat.GmsInfo
import com.android.internal.gmscompat.GmcBinderDefs.BinderDefStateListener

enum class BinderDefGroup(val services: Array<BinderDefSupplier>) {
    LOCATION(arrayOf(GLocationService.BinderDef()))
}

object BinderDefs {

    private val ifaceNameToGroup = ArrayMap<String, BinderDefGroup>().also { map ->
        BinderDefGroup.values().forEach { group ->
            group.services.forEach {
                map.put(it.ifaceName, group)
            }
        }
    }

    private val ifaceNameToBinderDef = ArrayMap<String, BinderDefSupplier>().also { map ->
        BinderDefGroup.values().forEach { group ->
            group.services.forEach {
                map.put(it.ifaceName, it)
            }
        }
    }

    fun getFromIfaceNameIfEnabled(callerPkg: String, ifaceName: String): BinderDef? {
        if (isEnabled(callerPkg, ifaceName)) {
            return getFromIfaceName(callerPkg, ifaceName)
        }
        return null
    }

    fun getFromIfaceName(callerPkg: String, ifaceName: String): BinderDef {
        return ifaceNameToBinderDef.get(ifaceName)!!.get(App.ctx(), callerPkg)
    }

    private fun getGroupFromIfaceName(ifaceName: String): BinderDefGroup {
        return ifaceNameToGroup.get(ifaceName)!!
    }

    private fun isEnabled(callerPkg: String, ifaceName: String): Boolean {
        return isEnabled(getGroupFromIfaceName(ifaceName), callerPkg)
    }

    fun isEnabled(group: BinderDefGroup, callerPkg: String? = null): Boolean {
        val prefs = App.preferences()
        return when (group) {
            BinderDefGroup.LOCATION -> {
                val k = MainProcessPrefs.LOCATION_REQUEST_REDIRECTION_ENABLED
                // getLong is used for compatibility with historical setting key
                val v = prefs.getLong(k, -1L)
                if (v == -1L) {
                    val isEnabledByDefault = !gmsCoreHasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    prefs.edit {
                        putLong(k, if (isEnabledByDefault) 1 else 0)
                    }
                    return isEnabledByDefault
                }
                v == 1L
            }
        }
    }

    fun setEnabled(group: BinderDefGroup, enabled: Boolean) {
        App.preferences().edit {
            when (group) {
                BinderDefGroup.LOCATION ->
                    // putLong is used for compatibility with historical setting key
                    putLong(MainProcessPrefs.LOCATION_REQUEST_REDIRECTION_ENABLED, if (enabled) 1 else 0)
            }
        }

        val changedIfaceNames: Array<String> = group.services.map { it.ifaceName }.toTypedArray()

        val intent = Intent(BinderDefStateListener.INTENT_ACTION).apply {
            putExtra(BinderDefStateListener.KEY_CHANGED_IFACE_NAMES, changedIfaceNames)
        }
        App.ctx().sendBroadcast(intent)
    }

    val binderDefsForNonGmsCoreClients: Set<String> = getIfaceNames(BinderDefGroup.LOCATION)
    val notableIfaceNames: Map<String, NotableInterface> = NotableInterface.values().associateBy { it.ifaceName }

    fun maybeGetBinderDef(callerPkg: String, processState: Int, ifaceName: String, isFromGms2Gca: Boolean): BinderDef? {
        // Note that the callerPkg value is not verified at this point, verification is delayed
        // until the time of first use to reduce perf impact

        if (IS_DEBUGGABLE) {
            logd{"caller $callerPkg processState ${ActivityManager.procStateToString(processState)} $ifaceName"}
        }

        val notableIface = notableIfaceNames.get(ifaceName)
        if (notableIface != null) {
            verifyCallerPkg(callerPkg)

            App.bgExecutor().execute {
                notableIface.onAcquiredByClient(callerPkg, processState)
            }
        }

        if (callerPkg == GmsInfo.PACKAGE_GMS_CORE) {
            check(isFromGms2Gca)
            return null
        }

        if (binderDefsForNonGmsCoreClients.contains(ifaceName)) {
            verifyCallerPkg(callerPkg)

            return getFromIfaceNameIfEnabled(callerPkg, ifaceName)
        }

        return null
    }

    fun getIfaceNames(vararg groups: BinderDefGroup): Set<String> {
        val set = ArraySet<String>()
        groups.forEach {
            it.services.forEach {
                set.add(it.ifaceName)
            }
        }
        return set
    }
}
