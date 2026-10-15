package tachiyomi.presentation.core.components.material

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabTextTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun badgeRemainsVisibleWhenTabTitleIsConstrained() {
        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .clipToBounds(),
            ) {
                TabText(text = "Extensions", badgeCount = 26)
            }
        }

        composeTestRule.onNodeWithText("26").assertIsDisplayed()
    }
}
