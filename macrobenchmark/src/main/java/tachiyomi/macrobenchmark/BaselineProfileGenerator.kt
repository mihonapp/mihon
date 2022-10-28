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
            device.findObject(By.text("More")).click()

            // TODO: Navigate to browse-extensions screen when storage permission
            // in sources screen moved. Possibly open manga details screen too?
            // device.findObject(By.text("Browse")).click()
        },
    )
}
