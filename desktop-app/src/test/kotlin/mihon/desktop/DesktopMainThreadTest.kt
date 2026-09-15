package mihon.desktop

import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities
import kotlin.coroutines.EmptyCoroutineContext

class DesktopMainThreadTest {
    @Test
    fun `packaged host dispatcher preserves Swing main thread for Compose lifecycle`() {
        Dispatchers.Main.immediate.isDispatchNeeded(EmptyCoroutineContext) shouldBe true
        SwingUtilities.invokeAndWait {
            Dispatchers.Main.immediate.isDispatchNeeded(EmptyCoroutineContext) shouldBe false
            val owner = object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry(this)
            }
            owner.lifecycle.addObserver(LifecycleEventObserver { _, _ -> })
        }
    }
}
