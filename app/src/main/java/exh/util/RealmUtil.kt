package exh.util

import io.realm.Realm
import io.realm.RealmModel
import io.realm.log.RealmLog
import java.util.*

inline fun <T> realmTrans(block: (Realm) -> T): T {
    return defRealm {
        it.trans {
            block(it)
        }
    }
}

inline fun <T> defRealm(block: (Realm) -> T): T {
    return Realm.getDefaultInstance().use {
        block(it)
    }
}

inline fun <T> Realm.trans(block: () -> T): T {
    beginTransaction()
    try {
        val res = block()
        commitTransaction()
        return res
    } catch(t: Throwable) {
        if (isInTransaction) {
            cancelTransaction()
        } else {
            RealmLog.warn("Could not cancel transaction, not currently in a transaction.")
        }

        throw t
    } finally {
        //Just in case
        if (isInTransaction) {
            cancelTransaction()
        }
    }
}

inline fun <T> Realm.useTrans(block: (Realm) -> T): T {
    return use {
        trans {
            block(this)
        }
    }
}

fun <T : RealmModel> Realm.createUUIDObj(clazz: Class<T>)
    = createObject(clazz, UUID.randomUUID().toString())!!

inline fun <reified T : RealmModel> Realm.createUUIDObj()
        = createUUIDObj(T::class.java)

