package mihon.sync.merge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tachiyomi.domain.category.model.Category
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * The account's categories as one shared list, and how two versions of it are reconciled.
 *
 * Three properties make it hold up when devices change categories independently:
 *
 * - Every category has a stable id, apart from its name and its position. Library entries refer to
 *   categories by that id, so a rename or a reorder on one device can never send an entry into the
 *   wrong category elsewhere — which is what referring to positions did as soon as two devices
 *   disagreed on the order.
 * - Every change carries the time it was made, and the newer one wins, category by category. Two
 *   devices that each changed a different category both keep their change, and a change that was
 *   overwritten by a device working from an older list is put back on the next round.
 * - A deletion is recorded, never inferred from a category being absent. A device that simply had
 *   not heard of a category yet cannot delete it, and a deleted category cannot come back from a
 *   device that had not heard of the deletion.
 *
 * Every rule here gives the same answer whichever side is the local one, so devices settle on a
 * single list instead of each holding on to its own.
 */
object SyncCategoryMerge {

    /**
     * Every id is at least this large, far above any position a category can have, so a reference
     * written by the first version of the sync — a position — is never mistaken for an id.
     */
    const val ID_FLOOR = 1L shl 40

    private const val ID_MASK = (1L shl 62) - 1

    fun isSyncId(value: Long): Boolean = value >= ID_FLOOR

    @Serializable
    data class Entry(
        @SerialName("id") val id: Long,
        @SerialName("name") val name: String,
        @SerialName("flags") val flags: Long = 0,
        /** A tombstone: the category was deleted, and stays deleted unless changed again later. */
        @SerialName("deleted") val deleted: Boolean = false,
        /** When this version was decided, in milliseconds. Zero for "no decision known". */
        @SerialName("modifiedAt") val modifiedAt: Long = 0,
    )

    @Serializable
    data class Catalogue(
        @SerialName("categories") val entries: List<Entry> = emptyList(),
        /** Ids of the live categories, in display order. */
        @SerialName("order") val order: List<Long> = emptyList(),
        @SerialName("orderModifiedAt") val orderModifiedAt: Long = 0,
    ) {
        /** The categories that exist, in display order. */
        val live: List<Entry>
            get() {
                val byId = entries.associateBy { it.id }
                return order.mapNotNull { id -> byId[id]?.takeUnless { it.deleted } }
            }
    }

    /**
     * What a device knew after its last reconciliation: the list it settled on, and which of its own
     * categories each id stands for. Comparing against it is how a later change is recognised as a
     * rename rather than a deletion plus a creation, and dated as a change made since.
     */
    @Serializable
    data class Snapshot(
        @SerialName("catalogue") val catalogue: Catalogue,
        /** Sync id to local category id, for every live category. */
        @SerialName("localIds") val localIds: Map<Long, Long>,
    )

    /** A device's categories expressed as a catalogue, and the local category behind each id. */
    data class LocalView(val catalogue: Catalogue, val localIds: Map<Long, Long>)

    /**
     * Reconciles two versions of the list. The result is the same whichever one is passed first.
     */
    fun merge(a: Catalogue, b: Catalogue): Catalogue {
        val entries = (a.entries + b.entries)
            .groupBy { it.id }
            .values
            .map { versions -> versions.maxWith(precedence) }

        val (leading, trailing) = when {
            a.orderModifiedAt > b.orderModifiedAt -> a to b
            b.orderModifiedAt > a.orderModifiedAt -> b to a
            compareOrders(a.order, b.order) <= 0 -> a to b
            else -> b to a
        }

        return normalize(entries, leading.order + trailing.order, leading.orderModifiedAt)
    }

    /**
     * This device's categories as a catalogue, dated against what it knew after the last round.
     *
     * A category that matches nothing known yet is recognised by name: the same category made on
     * another device takes that device's id — and none of its authority, so the version already
     * shared wins — while a name the account deleted is revived when it is made again here.
     * Everything else is new, and dated [now].
     */
    fun localView(local: List<Category>, snapshot: Snapshot?, remote: Catalogue, now: Long): LocalView {
        val categories = local.filterNot(Category::isSystemCategory).sortedBy(Category::order)
        val presentIds = categories.mapTo(HashSet()) { it.id }
        val previousById = snapshot?.catalogue?.entries?.associateBy { it.id }.orEmpty()
        val syncIdByLocalId = snapshot?.localIds.orEmpty().entries.associate { (syncId, localId) -> localId to syncId }

        val entries = LinkedHashMap<Long, Entry>()
        val localIds = LinkedHashMap<Long, Long>()

        // Deletions already settled stay recorded, so a list that lost them cannot bring them back.
        previousById.values.filter { it.deleted }.forEach { entries[it.id] = it }

        // Categories reconciled before, matched by local id so that a rename is seen as one.
        val unmatched = mutableListOf<Category>()
        for (category in categories) {
            val previous = syncIdByLocalId[category.id]?.let(previousById::get)
            if (previous == null || previous.deleted) {
                unmatched += category
                continue
            }
            val unchanged = previous.name == category.name && previous.flags == category.flags
            entries[previous.id] = previous.copy(
                name = category.name,
                flags = category.flags,
                modifiedAt = if (unchanged) previous.modifiedAt else now,
            )
            localIds[previous.id] = category.id
        }

        // Categories reconciled before and gone since: deleted on this device.
        val deletedNow = mutableSetOf<Long>()
        snapshot?.localIds?.forEach { (syncId, localId) ->
            if (localId in presentIds) return@forEach
            val previous = previousById[syncId]?.takeUnless { it.deleted } ?: return@forEach
            entries[syncId] = previous.copy(deleted = true, modifiedAt = now)
            deletedNow += syncId
        }

        val taken = (remote.entries.map { it.id } + previousById.keys + entries.keys).toMutableSet()
        val sharedByName = remote.entries.filterNot { it.deleted }.associateBy { it.name }
        val buriedByName = (remote.entries + previousById.values)
            .filter { it.deleted }
            .groupBy { it.name }
            .mapValues { (_, tombstones) -> tombstones.maxBy { it.modifiedAt } }

        // Made again after the account deleted it. On a first round there is no telling whether that
        // happened before or after the deletion, so the deletion stands.
        val revivedAt = if (snapshot == null) 0L else now

        for (category in unmatched) {
            val shared = sharedByName[category.name]?.takeIf { it.id !in entries }
            val buried = buriedByName[category.name]?.takeIf { it.id !in localIds && it.id !in deletedNow }
            val entry = when {
                shared != null -> Entry(shared.id, category.name, category.flags)
                buried != null -> Entry(buried.id, category.name, category.flags, modifiedAt = revivedAt)
                else -> Entry(newId(category.name, taken), category.name, category.flags, modifiedAt = now)
            }
            taken += entry.id
            entries[entry.id] = entry
            localIds[entry.id] = category.id
        }

        // Only moving categories around counts as a reorder: adding or removing one does not, or it
        // would override a reorder made meanwhile on another device.
        val order = categories.map { category -> localIds.entries.first { it.value == category.id }.key }
        val previousOrder = snapshot?.catalogue?.order.orEmpty()
        val kept = order.filter { it in previousOrder }
        val previouslyKept = previousOrder.filter { it in order }
        val orderModifiedAt = when {
            snapshot == null -> 0
            kept != previouslyKept -> now
            else -> snapshot.catalogue.orderModifiedAt
        }

        return LocalView(
            catalogue = Catalogue(entries.values.sortedBy { it.id }, order, orderModifiedAt),
            localIds = localIds,
        )
    }

    /**
     * Categories as the first version of the sync kept them: a name and a position, nothing more.
     * Ids are derived from the names, so every device converting the same list arrives at the same
     * ones.
     */
    fun fromLegacy(categories: List<Category>): Catalogue {
        val taken = mutableSetOf<Long>()
        val entries = categories
            .filterNot(Category::isSystemCategory)
            .sortedBy(Category::order)
            .distinctBy(Category::name)
            .map { category ->
                val id = newId(category.name, taken)
                taken += id
                Entry(id, category.name, category.flags)
            }
        return Catalogue(entries.sortedBy { it.id }, entries.map { it.id })
    }

    /**
     * An id for a category first seen under [name]. Derived from the name, so two devices creating
     * the same category before they have synced arrive at the same id instead of a duplicate.
     */
    fun newId(name: String, taken: Set<Long>): Long {
        var attempt = 0
        while (true) {
            val candidate = idFor(if (attempt == 0) name else "$name#$attempt")
            if (candidate !in taken) return candidate
            attempt++
        }
    }

    private fun idFor(seed: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
        return (ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).long and ID_MASK) or ID_FLOOR
    }

    /**
     * Newer wins. On a tie the result only has to be the same on every device: keeping a category
     * beats deleting it, and the rest is an arbitrary but fixed order.
     */
    private val precedence = compareBy<Entry>({ it.modifiedAt }, { !it.deleted }, { it.name }, { it.flags })

    private fun compareOrders(first: List<Long>, second: List<Long>): Int {
        for (index in 0..<minOf(first.size, second.size)) {
            val comparison = first[index].compareTo(second[index])
            if (comparison != 0) return comparison
        }
        return first.size.compareTo(second.size)
    }

    private fun normalize(entries: List<Entry>, preferredOrder: List<Long>, orderModifiedAt: Long): Catalogue {
        val live = entries.filterNot { it.deleted }.associateBy { it.id }
        val order = (
            preferredOrder.filter { it in live } +
                live.values.sortedWith(compareBy({ it.name }, { it.id })).map { it.id }
            )
            .distinct()

        val renamed = uniqueNames(order.map(live::getValue))
        val settled = entries
            .map { entry -> renamed[entry.id]?.let { entry.copy(name = it) } ?: entry }
            .sortedBy { it.id }

        return Catalogue(settled, order, orderModifiedAt)
    }

    /**
     * Two live categories cannot share a name: the app keeps names unique, and entries are matched to
     * categories by name when applied. The lowest id keeps the name and the others get a suffix, so
     * every device settles the clash the same way.
     */
    private fun uniqueNames(live: List<Entry>): Map<Long, String> {
        val renamed = mutableMapOf<Long, String>()
        val used = mutableSetOf<String>()
        for (entry in live.sortedBy { it.id }) {
            var name = entry.name
            var suffix = 2
            while (name in used) {
                name = "${entry.name} ($suffix)"
                suffix++
            }
            used += name
            if (name != entry.name) renamed[entry.id] = name
        }
        return renamed
    }
}
