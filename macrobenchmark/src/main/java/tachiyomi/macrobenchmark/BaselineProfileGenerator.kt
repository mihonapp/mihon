package tachiyomi.macrobenchmark

import androidx.benchmark.macro.ExperimentalBaselineProfilesApi
import androidx.benchmark.macro.junit4.BaselineProfileRule
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

            // TODO: Navigate to browse-extensions screen when storage permission
            // in sources screen moved. Possibly open manga details screen too?
        },
    )
}
