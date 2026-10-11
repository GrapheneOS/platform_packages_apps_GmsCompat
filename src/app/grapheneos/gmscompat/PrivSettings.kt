package app.grapheneos.gmscompat

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.android.internal.gmscompat.GmcPrivSettingsHooks
import com.android.internal.gmscompat.GmsCompatApp
import java.util.Arrays

// A partial reimplementation of Settings.{Global,Secure} and DeviceConfig.
class PrivSettings(val context: Context) {
    private val storageMap = HashMap<String, SharedPreferences>()

    private val DBG = false

    private fun storage(namespace: String): SharedPreferences {
        return synchronized(storageMap) {
            storageMap.getOrPut(namespace) {
                App.deviceProtectedStorageContext()
                        .getSharedPreferences(namespace, Context.MODE_PRIVATE)
            }
        }
    }

    fun getString(ns: String, key: String): String? {
        val storage = storage(ns)
        val value = synchronized(storage) {
            storage.getString(key, null)
        }
        if (DBG) logd{"$ns $key $value"}
        return value
    }

    fun putString(ns: String, key: String, value: String?): Boolean {
        if (DBG) logd{"$ns $key $value"}

        val storage = storage(ns)
        val res = synchronized(storage) {
            storage.edit().putString(key, value).commit()
        }

        notifyObservers(ns, key)
        return res
    }

    fun getStrings(ns: String, keys: Array<String>,
            foundKeys: MutableList<String>, foundValues: MutableList<String>) {
        if (DBG) logd{"$ns keys  " + Arrays.toString(keys)}

        val storage = storage(ns)
        synchronized(storage) {
            keys.forEach { key ->
                storage.getString(key, null)?.let { value ->
                    if (DBG) logd{"$ns $key $value"}
                    foundKeys.add(key)
                    foundValues.add(value)
                }
            }
        }
    }

    fun putStrings(ns: String, keys: List<String>, values: List<String>): Boolean {
        if (DBG) {
            for (i in 0 until keys.size) {
                logd{"$ns ${keys[i]} = ${values[i]}"}
            }
        }

        val storage = storage(ns)

        val res = synchronized(storage) {
            val ed = storage.edit()
            /** @see android.provider.DeviceConfig#setProperties */
            ed.clear()
            for (i in 0 until keys.size) {
                ed.putString(keys[i], values[i])
            }
            ed.commit()
        }

        keys.forEach {
            notifyObservers(ns, it)
        }

        return res
    }

    private fun notifyObservers(ns: String, key: String) {
        if (ns.startsWith(GmcPrivSettingsHooks.DEVICE_CONFIG_NAMESPACE)) {
            // not supported and not used by clients
            return
        }

        val intent = Intent(GmcPrivSettingsHooks.ACTION_SETTING_CHANGED).apply {
            putExtra(GmcPrivSettingsHooks.EXTRA_SETTING_NAMESPACE, ns)
            putExtra(GmcPrivSettingsHooks.EXTRA_SETTING_KEY, key)
        }
        context.sendBroadcast(intent, GmsCompatApp.CLIENT_PERMISSION)
    }
}
