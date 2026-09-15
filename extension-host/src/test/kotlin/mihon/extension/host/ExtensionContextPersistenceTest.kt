package mihon.extension.host

import android.content.Context
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.extension.model.ExtensionManifest
import mihon.extension.model.SourceDescriptor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtensionContextPersistenceTest {
    class ContextFactory : SourceFactory {
        init {
            contexts.add(Injekt.get<Context>())
        }
        override fun createSources(): List<Source> = emptyList()
        companion object {
            val contexts = mutableListOf<Context>()
        }
    }

    private fun load(root: Path, id: String): Context {
        val manifest = ExtensionManifest(
            id,
            "Context",
            "1.0.0",
            1,
            1.4,
            "en",
            sources = listOf(SourceDescriptor(91L, "Context", "en", ContextFactory::class.java.name)),
        )
        val archive = root.resolve("$id.mext").toFile()
        ZipOutputStream(archive.outputStream()).use {
            it.putNextEntry(ZipEntry("manifest.json"))
            it.write(Json.encodeToString(manifest).toByteArray())
            it.closeEntry()
            it.putNextEntry(ZipEntry("assets/test/nested.txt"))
            it.write("asset body".toByteArray())
            it.closeEntry()
        }
        ExtensionHostEngine(BrokeredHttpClient { null }).loadExtension(archive, root.resolve("work-$id").toFile())
        return ContextFactory.contexts.last()
    }

    @Test
    fun `extension assets are available through Android context`(@TempDir root: Path) {
        val context = load(root, "ext.assets")
        val getter = context.javaClass.methods.firstOrNull { it.name == "getAssets" }
        (getter != null) shouldBe true
        val assets = getter!!.invoke(context)
        val stream = assets.javaClass.getMethod(
            "open",
            String::class.java,
        ).invoke(assets, "test/nested.txt") as java.io.InputStream
        stream.bufferedReader().use { it.readText() } shouldBe "asset body"
    }

    @Test
    fun `extension context is configured before factory construction and survives reload`(@TempDir root: Path) {
        val first = load(root, "ext.one")
        first.getFilesDir().toPath().startsWith(root) shouldBe true
        first.getCacheDir().toPath().startsWith(root) shouldBe true
        first.getFilesDir().resolve("marker").writeText("survives")
        first.getSharedPreferences("settings", 0).edit().putString("token", "saved").commit() shouldBe true
        val restored = load(root, "ext.one")
        restored.getFilesDir().resolve("marker").readText() shouldBe "survives"
        restored.getSharedPreferences("settings", 0).getString("token", null) shouldBe "saved"
    }

    @Test
    fun `extensions isolate files cache and identically named preferences`(@TempDir root: Path) {
        val one = load(root, "ext.one")
        one.getSharedPreferences("settings", 0).edit().putInt("value", 7).commit()
        val two = load(root, "ext.two")
        (one.getFilesDir() == two.getFilesDir()) shouldBe false
        (one.getCacheDir() == two.getCacheDir()) shouldBe false
        two.getSharedPreferences("settings", 0).contains("value") shouldBe false
        one.getSharedPreferences("settings", 0).getInt("value", 0) shouldBe 7
    }

    @Test
    fun `preferences restore typed values apply remove clear and defensive sets`(@TempDir root: Path) {
        val prefs = load(root, "ext.types").getSharedPreferences("settings", 0)
        val values = mutableSetOf("a", "b")
        prefs.edit().putStringSet("set", values).putLong("long", Long.MAX_VALUE)
            .putFloat("float", 1.25f).putBoolean("bool", true).putInt("int", 42)
            .putString("gone", "x").apply()
        values.add("outside")
        (prefs.getStringSet("set", null) as MutableSet<String>).add("read-mutation")
        prefs.getStringSet("set", null) shouldBe setOf("a", "b")
        val restored = load(root, "ext.types").getSharedPreferences("settings", 0)
        restored.getLong("long", 0) shouldBe Long.MAX_VALUE
        restored.getFloat("float", 0f) shouldBe 1.25f
        restored.getBoolean("bool", false) shouldBe true
        restored.getInt("int", 0) shouldBe 42
        restored.edit().remove("gone").putStringSet("set", null).commit() shouldBe true
        val editor = restored.edit().putString("keep", "yes").clear()
        editor.commit() shouldBe true
        restored.getAll() shouldBe mapOf("keep" to "yes")
        restored.edit().putInt("later", 1).commit()
        editor.putString("again", "ok").commit()
        load(root, "ext.types").getSharedPreferences("settings", 0).getInt("later", 0) shouldBe 1
    }

    @Test
    fun `preferences preserve long string values beyond modified UTF limits`(@TempDir root: Path) {
        val value = "长".repeat(70000)
        val preferences = load(root, "ext.large").getSharedPreferences("large", 0)
        preferences.edit().putString("text", value).commit() shouldBe true
        load(root, "ext.large").getSharedPreferences("large", 0).getString("text", null) shouldBe value
    }

    @Test
    fun `extension identity follows Rx worker scheduling`(@TempDir root: Path) {
        val application = load(root, "ext.rx") as android.app.Application
        val identity = ExtensionExecutionContext.Identity("ext.rx", 77L, application)
        val result = ExtensionExecutionContext.duringConstruction(identity) {
            rx.Observable.fromCallable {
                Triple(
                    ExtensionExecutionContext.currentPackageId(),
                    ExtensionExecutionContext.currentSourceId(),
                    Injekt.get<Context>().getFilesDir(),
                )
            }.subscribeOn(rx.schedulers.Schedulers.io()).toBlocking().single()
        }
        result shouldBe Triple("ext.rx", 77L, application.getFilesDir())
        ExtensionExecutionContext.currentPackageId() shouldBe null
    }
}
