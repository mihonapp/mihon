package android.content

import android.app.Application
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ContextWrapperCompatibilityTest {
    @Test
    fun `Application has Android wrapper hierarchy with persistent delegated values`(@TempDir directory: Path) {
        val app: ContextWrapper = Application(directory.toFile())
        val wrapper = ContextWrapper(app)
        wrapper.getSharedPreferences("source", 0).edit().putString("key", "value").commit()
        assertEquals("value", app.getSharedPreferences("source", 0).getString("key", null))
        assertSame(app, wrapper.getApplicationContext())
        assertThrows(ActivityNotFoundException::class.java) { wrapper.startActivity(Intent(Intent.ACTION_VIEW)) }
        assertThrows(UnsupportedOperationException::class.java) { android.app.Activity() }
        assertEquals(160, wrapper.getResources().getDisplayMetrics().densityDpi)
        assertThrows(android.content.res.Resources.NotFoundException::class.java) {
            wrapper.getResources().getString(1)
        }
    }
}
