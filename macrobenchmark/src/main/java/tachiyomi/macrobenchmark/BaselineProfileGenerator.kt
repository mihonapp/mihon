package tachiyomi.macrobenchmark

import androidx.benchmark.macro.ExperimentalBaselineProfilesApi
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalBaselineProfilesApi::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collectBaselineProfile(
        packageName = "eu.kanade.tachiyomi.benchmark",
        profileBlock = {
            pressHome()
            startActivityAndWait()

            device.findObject(By.text("Updates")).click()

            device.findObject(By.text("History")).click()

            // TODO: automate storage permissions and possibly open manga details screen too?
            // device.findObject(By.text("Browse")).click()
            // device.findObject(By.text("Extensions")).click()
            // device.swipe(150, 150, 50, 150, 1)

            device.findObject(By.text("More")).click()
            device.findObject(By.text("Settings")).click()
        },
    )
}
