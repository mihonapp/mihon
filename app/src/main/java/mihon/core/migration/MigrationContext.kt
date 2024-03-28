package mihon.core.migration

import uy.kohesive.injekt.Injekt

class MigrationContext(val dryrun: Boolean) {

    inline fun <reified T> get(): T? {
        return Injekt.getInstanceOrNull(T::class.java)
    }
}
