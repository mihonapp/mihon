package exh.util

import io.realm.Realm
import io.realm.RealmModel
import io.realm.log.RealmLog
import java.util.*

inline fun <T> realmTrans(block: (Realm) -> T): T {
    return defRealm {
        it.beginTransaction()
        try {
            val res = block(it)
            it.commitTransaction()
            res
        } catch(t: Throwable) {
            if (it.isInTransaction) {
                it.cancelTransaction()
            } else {
                RealmLog.warn("Could not cancel transaction, not currently in a transaction.")
            }

            throw t
        } finally {
            //Just in case
            if (it.isInTransaction) {
                it.cancelTransaction()
            }
        }
    }
}

inline fun <T> defRealm(block: (Realm) -> T): T {
    return Realm.getDefaultInstance().use {
        block(it)
    }
}

fun <T : RealmModel> Realm.createUUIDObj(clazz: Class<T>)
    = createObject(clazz, UUID.randomUUID().toString())
