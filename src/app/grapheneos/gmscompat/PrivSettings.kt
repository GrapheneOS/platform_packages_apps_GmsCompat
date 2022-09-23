package app.grapheneos.gmscompat

import android.content.Context
import android.content.SharedPreferences
import android.database.IContentObserver
import android.net.Uri
import android.os.DeadObjectException
import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import android.provider.Settings
import android.util.ArraySet
import android.util.Pair
import java.util.*

typealias NsKey = Pair<String, String>

// A partial reimplementation of Settings.{Global,Secure} and DeviceConfig.
class PrivSettings : IBinder.DeathRecipient {
    private val storageMap = HashMap<String, SharedPreferences>()
    private val observerMap = HashMap<NsKey, ArraySet<IContentObserver>>()
    private val reverseObserverMap = HashMap<IBinder, ArraySet<NsKey>>()

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

    fun putStrings(ns: String, keys: Array<String>, values: Array<String>): Boolean {
        if (DBG) logd{"$ns keys " + Arrays.toString(keys) + " values " + Arrays.toString(values)}

        val storage = storage(ns)

        val res = synchronized(storage) {
            val ed = storage.edit()
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

    fun addObserver(ns: String, key: String, observer: IContentObserver) {
        if (DBG) logd{"$ns $key ${observer.asBinder()}"}

        val nsKey = NsKey.create(ns, key)
        val binder = observer.asBinder()

        synchronized(observerMap) {
            if (reverseObserverMap[binder] == null) {
                try {
                    binder.linkToDeath(this, 0)
                } catch (e: RemoteException) {
                    logd{"observer already died " + e}
                    return
                }
            }

            observerMap.getOrPut(nsKey) {
                ArraySet<IContentObserver>()
            }.add(observer)

            reverseObserverMap.getOrPut(binder) {
                ArraySet<NsKey>()
            }.add(nsKey)
        }
    }

    override fun binderDied() {
        // should never be reached if binderDied(IBinder) is overriden
        throw IllegalStateException()
    }

    override fun binderDied(who: IBinder) {
        removeObserver(who)
    }

    fun removeObserver(observer: IContentObserver) {
        removeObserver(observer.asBinder())
    }

    private fun removeObserver(observer: IBinder) {
        if (DBG) logd{"$observer" + Throwable()}

        synchronized(observerMap) {
            observer.unlinkToDeath(this, 0)

            val nsKeys = reverseObserverMap[observer] ?: return
            reverseObserverMap.remove(observer)

            nsKeys.forEach { nsKey ->
                val set = observerMap[nsKey] ?: return@forEach

                var found = false

                for (i in 0 until set.size) {
                    if (set.valueAt(i).asBinder() === observer) {
                        set.removeAt(i)
                        found = true
                        break
                    }
                }

                if (DBG) check(found)

                if (set.size == 0) {
                    observerMap.remove(nsKey)
                }
            }
        }
    }

    private fun notifyObservers(ns: String, key: String, flags: Int = 0) {
        val nsKey = NsKey(ns, key)
        val namespaceUri = Uri.parse("content://${Settings.AUTHORITY}/$ns")
        val uri = Settings.NameValueTable.getUriFor(namespaceUri, key)
        val uriArray = arrayOf(uri)
        val userId = Process.myUserHandle().identifier

        synchronized(observerMap) {
            val observers = observerMap[nsKey]?.toTypedArray() ?: return

            if (DBG) logd{"$ns $key to ${observers.size} observers"}

            observers.forEach {
                try {
                    it.onChangeEtc(false, uriArray, flags, userId)
                } catch (doe: DeadObjectException) {
                    logd{"listener died $doe"}
                }
            }
        }
    }
}
