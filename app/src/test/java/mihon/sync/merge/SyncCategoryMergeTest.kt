package mihon.sync.merge

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import mihon.sync.merge.SyncCategoryMerge.Catalogue
import mihon.sync.merge.SyncCategoryMerge.Entry
import mihon.sync.merge.SyncCategoryMerge.Snapshot
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.category.model.Category

@Execution(ExecutionMode.CONCURRENT)
class SyncCategoryMergeTest {

    // Two devices and the shared list, going through rounds the way the sync does.

    @Test
    fun `a category created on one device appears on the other`() {
        val drive = Drive()
        val phone = Device().apply {
            create("Reading")
            create("Done")
        }
        val tablet = Device()

        phone.sync(drive, at = 100)
        tablet.sync(drive, at = 200)

        tablet.names() shouldContainExactly listOf("Reading", "Done")
    }

    @Test
    fun `a rename keeps the category, and so the entries in it`() {
        val drive = Drive()
        val phone = Device().apply { create("Reading") }
        val tablet = Device()
        phone.sync(drive, at = 100)
        tablet.sync(drive, at = 200)
        val category = tablet.localId("Reading")

        phone.rename("Reading", "En cours")
        phone.sync(drive, at = 300)
        tablet.sync(drive, at = 400)

        tablet.names() shouldContainExactly listOf("En cours")
        tablet.localId("En cours") shouldBe category
    }

    @Test
    fun `a deletion reaches the other device and does not come back`() {
        val drive = Drive()
        val phone = Device().apply {
            create("Reading")
            create("Done")
        }
        val tablet = Device()
        phone.sync(drive, at = 100)
        tablet.sync(drive, at = 200)

        phone.delete("Done")
        phone.sync(drive, at = 300)
        tablet.sync(drive, at = 400)
        phone.sync(drive, at = 500)
        tablet.sync(drive, at = 600)

        phone.names() shouldContainExactly listOf("Reading")
        tablet.names() shouldContainExactly listOf("Reading")
    }

    @Test
    fun `a reorder reaches the other device`() {
        val drive = Drive()
        val phone = Device().apply {
            create("A")
            create("B")
            create("C")
        }
        val tablet = Device()
        phone.sync(drive, at = 100)
        tablet.sync(drive, at = 200)

        phone.reorder("C", "A", "B")
        phone.sync(drive, at = 300)
        tablet.sync(drive, at = 400)

        tablet.names() shouldContainExactly listOf("C", "A", "B")
    }

    @Test
    fun `display settings reach the other device`() {
        val drive = Drive()
        val phone = Device().apply { create("Reading") }
        val tablet = Device()
        phone.sync(drive, at = 100)
        tablet.sync(drive, at = 200)

        phone.setFlags("Reading", 6)
        phone.sync(drive, at = 300)
        tablet.sync(drive, at = 400)

        tablet.flagsOf("Reading") shouldBe 6
    }

    @Test
    fun `changes to different categories on both devices are both kept`() {
        val drive = Drive()
        val phone = Device().apply {
            create("A")
            create("B")
        }
        val tablet = Device()
        phone.sync(drive, at = 100)
        tablet.sync(drive, at = 200)

        phone.rename("A", "A2")
        tablet.rename("B", "B2")
        phone.sync(drive, at = 300)
        tablet.sync(drive, at = 400)
        phone.sync(drive, at = 500)

        phone.names() shouldContainExactly listOf("A2", "B2")
        tablet.names() shouldContainExactly listOf("A2", "B2")
    }

    @Test
    fun `the same name created on two devices before they sync is one category`() {
        val drive = Drive()
        val phone = Device().apply { create("Reading") }
        val tablet = Device().apply { create("Reading") }

        phone.sync(drive, at = 100)
        tablet.sync(drive, at = 200)
        phone.sync(drive, at = 300)

        phone.names() shouldContainExactly listOf("Reading")
        tablet.names() shouldContainExactly listOf("Reading")
    }

    @Test
    fun `a device joining late takes the shared settings rather than imposing its own`() {
        val drive = Drive()
        val phone = Device().apply { create("Reading", flags = 4) }
        val tablet = Device().apply { create("Reading", flags = 0) }

        phone.sync(drive, at = 100)
        tablet.sync(drive, at = 200)

        tablet.flagsOf("Reading") shouldBe 4
    }

    @Test
    fun `adding a category does not undo a reorder made elsewhere`() {
        val drive = Drive()
        val phone = Device().apply {
            create("A")
            create("B")
            create("C")
        }
        val tablet = Device()
        phone.sync(drive, at = 100)
        tablet.sync(drive, at = 150)

        tablet.reorder("C", "B", "A")
        tablet.sync(drive, at = 200)
        phone.create("D")
        phone.sync(drive, at = 300)

        phone.names() shouldContainExactly listOf("C", "B", "A", "D")
    }

    // Two devices writing the list at the same moment: the second upload replaces the first. What the
    // first device decided must survive that.

    @Test
    fun `a category the other device has not heard of yet is not deleted by it`() {
        val drive = Drive()
        val phone = Device()
        val tablet = Device()
        tablet.sync(drive, at = 50)
        val stale = drive.catalogue

        phone.create("New")
        phone.sync(drive, at = 100)
        drive.catalogue = tablet.reconcile(stale, at = 110)
        phone.sync(drive, at = 200)
        tablet.sync(drive, at = 300)

        phone.names() shouldContainExactly listOf("New")
        tablet.names() shouldContainExactly listOf("New")
    }

    @Test
    fun `a rename overwritten by a device working from an older list is put back`() {
        val drive = Drive()
        val phone = Device().apply { create("Reading") }
        val tablet = Device()
        phone.sync(drive, at = 100)
        tablet.sync(drive, at = 150)
        val stale = drive.catalogue

        phone.rename("Reading", "En cours")
        phone.sync(drive, at = 200)
        drive.catalogue = tablet.reconcile(stale, at = 210)
        phone.sync(drive, at = 300)
        tablet.sync(drive, at = 400)

        phone.names() shouldContainExactly listOf("En cours")
        tablet.names() shouldContainExactly listOf("En cours")
    }

    @Test
    fun `a deletion overwritten by a device working from an older list stays a deletion`() {
        val drive = Drive()
        val phone = Device().apply {
            create("Reading")
            create("Done")
        }
        val tablet = Device()
        phone.sync(drive, at = 100)
        tablet.sync(drive, at = 150)
        val stale = drive.catalogue

        phone.delete("Done")
        phone.sync(drive, at = 200)
        drive.catalogue = tablet.reconcile(stale, at = 210)
        phone.sync(drive, at = 300)
        tablet.sync(drive, at = 400)

        phone.names() shouldNotContain "Done"
        tablet.names() shouldNotContain "Done"
    }

    @Test
    fun `a category deleted and made again with the same name starts empty under a new id`() {
        val drive = Drive()
        val phone = Device().apply { create("Reading") }
        phone.sync(drive, at = 100)
        val before = drive.catalogue.live.single().id

        phone.delete("Reading")
        phone.create("Reading")
        phone.sync(drive, at = 200)

        val after = drive.catalogue.live.single()
        after.name shouldBe "Reading"
        after.id shouldNotBe before
    }

    // The merge itself.

    @Test
    fun `merging gives the same list whichever side is local`() {
        val first = SyncCategoryMerge.ID_FLOOR + 1
        val second = SyncCategoryMerge.ID_FLOOR + 2
        val third = SyncCategoryMerge.ID_FLOOR + 3
        val catalogues = listOf(
            Catalogue(),
            Catalogue(listOf(Entry(first, "A", modifiedAt = 100)), listOf(first)),
            Catalogue(listOf(Entry(first, "A2", modifiedAt = 100)), listOf(first)),
            Catalogue(listOf(Entry(first, "A", deleted = true, modifiedAt = 100)), emptyList()),
            Catalogue(
                listOf(Entry(first, "A", modifiedAt = 50), Entry(second, "B", flags = 4, modifiedAt = 300)),
                listOf(second, first),
                orderModifiedAt = 300,
            ),
            Catalogue(
                listOf(Entry(second, "B", modifiedAt = 300), Entry(third, "A", modifiedAt = 10)),
                listOf(third, second),
                orderModifiedAt = 300,
            ),
        )

        for (a in catalogues) {
            for (b in catalogues) {
                withClue("$a vs $b") {
                    SyncCategoryMerge.merge(a, b) shouldBe SyncCategoryMerge.merge(b, a)
                }
            }
        }
    }

    @Test
    fun `two categories never end up sharing a name`() {
        val first = SyncCategoryMerge.ID_FLOOR + 1
        val second = SyncCategoryMerge.ID_FLOOR + 2

        val merged = SyncCategoryMerge.merge(
            Catalogue(listOf(Entry(first, "Reading", modifiedAt = 100)), listOf(first)),
            Catalogue(listOf(Entry(second, "Reading", modifiedAt = 200)), listOf(second)),
        )

        merged.live.map { it.name } shouldContainExactly listOf("Reading", "Reading (2)")
        merged.live.single { it.id == first }.name shouldBe "Reading"
    }

    @Test
    fun `ids are derived from the name and never look like a position`() {
        val id = SyncCategoryMerge.newId("Reading", emptySet())

        id shouldBe SyncCategoryMerge.newId("Reading", emptySet())
        id shouldNotBe SyncCategoryMerge.newId("Done", emptySet())
        SyncCategoryMerge.newId("Reading", setOf(id)) shouldNotBe id
        SyncCategoryMerge.isSyncId(id) shouldBe true
        (0L..1_000L).none(SyncCategoryMerge::isSyncId) shouldBe true
    }

    @Test
    fun `the first version's list converts to the same ids on every device`() {
        val legacy = listOf(
            Category(id = 0, name = "", order = -1, flags = 0),
            Category(id = 7, name = "Done", order = 1, flags = 0),
            Category(id = 3, name = "Reading", order = 0, flags = 4),
        )

        val converted = SyncCategoryMerge.fromLegacy(legacy)

        converted shouldBe SyncCategoryMerge.fromLegacy(legacy.reversed())
        converted.live.map { it.name } shouldContainExactly listOf("Reading", "Done")
        converted.live.first().id shouldBe SyncCategoryMerge.newId("Reading", emptySet())
        converted.live.first().flags shouldBe 4
    }

    /** The shared list on Drive: whatever the last device to sync uploaded. */
    private class Drive(var catalogue: Catalogue = Catalogue())

    /** One device's categories, kept and updated the way [mihon.sync.SyncCategories] does it. */
    private class Device {
        private var categories = listOf<Category>()
        private var snapshot: Snapshot? = null
        private var nextLocalId = 1L

        fun create(name: String, flags: Long = 0) {
            categories = categories + Category(nextLocalId++, name, categories.size.toLong(), flags)
        }

        fun rename(from: String, to: String) {
            categories = categories.map { if (it.name == from) it.copy(name = to) else it }
        }

        fun delete(name: String) {
            categories = categories.filterNot { it.name == name }
        }

        fun setFlags(name: String, flags: Long) {
            categories = categories.map { if (it.name == name) it.copy(flags = flags) else it }
        }

        fun reorder(vararg names: String) {
            categories = names.mapIndexed { index, name ->
                categories.single { it.name == name }.copy(order = index.toLong())
            }
        }

        fun names() = categories.sortedBy { it.order }.map { it.name }

        fun flagsOf(name: String) = categories.single { it.name == name }.flags

        fun localId(name: String) = categories.single { it.name == name }.id

        fun sync(drive: Drive, at: Long) {
            drive.catalogue = reconcile(drive.catalogue, at)
        }

        /** One round against [remote]: returns what this device uploads, and applies it locally. */
        fun reconcile(remote: Catalogue, at: Long): Catalogue {
            val view = SyncCategoryMerge.localView(categories, snapshot, remote, at)
            val merged = SyncCategoryMerge.merge(remote, view.catalogue)

            val localIds = view.localIds.toMutableMap()
            for (entry in merged.entries.filter { it.deleted }) {
                val localId = localIds.remove(entry.id) ?: continue
                categories = categories.filterNot { it.id == localId }
            }
            for (entry in merged.live) {
                val localId = localIds[entry.id]
                categories = if (localId != null && categories.any { it.id == localId }) {
                    categories.map { if (it.id == localId) it.copy(name = entry.name, flags = entry.flags) else it }
                } else {
                    val created = Category(nextLocalId++, entry.name, categories.size.toLong(), entry.flags)
                    localIds[entry.id] = created.id
                    categories + created
                }
            }
            categories = merged.live.mapIndexed { index, entry ->
                categories.single { it.id == localIds[entry.id] }.copy(order = index.toLong())
            }

            val liveIds = merged.live.mapTo(HashSet()) { it.id }
            snapshot = Snapshot(merged, localIds.filterKeys { it in liveIds })
            return merged
        }
    }
}
