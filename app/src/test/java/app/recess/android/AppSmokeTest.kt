package app.recess.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import app.recess.android.ui.FamilyViewModel
import app.recess.core.ActivityKind
import app.recess.core.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/** Launches the real activity on the demo family and drives every screen and the core actions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val vm get() = ViewModelProvider(compose.activity)[FamilyViewModel::class.java]

    @Before fun freshDemo() {
        // Tests share the app's saved file, so start each one from the demo family.
        compose.runOnUiThread { vm.resetDemo() }
        compose.waitForIdle()
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        shown(text)
    }

    /** assertIsDisplayed, but the failure says where the node actually is. */
    private fun shown(text: String) {
        val node = compose.onNodeWithText(text).fetchSemanticsNode()
        val root = compose.onRoot().fetchSemanticsNode()
        val msg = "'$text' bounds=${node.boundsInRoot} root=${root.boundsInRoot} placed=${node.layoutInfo.isPlaced}"
        try {
            compose.onNodeWithText(text).assertIsDisplayed()
        } catch (e: AssertionError) {
            throw AssertionError(msg, e)
        }
    }

    @Test fun everyTabRenders() {
        compose.onNodeWithText("Sync Classroom (demo)").assertIsDisplayed()
        scrollTo("Recent activity")

        compose.onNodeWithTag("tab_classroom").performClick()
        shown("Everyone · 6 open")

        compose.onNodeWithTag("tab_rules").performClick()
        shown("Automation")
        scrollTo("Per child")

        compose.onNodeWithTag("tab_log").performClick()
        shown("Activity")

        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithText("Sign in with Google").assertExists()
        scrollTo("Demo family")
    }

    @Test fun childDetailGivesExtraTime() {
        val before = vm.state.value.children.first { it.id == Seed.MAYA }.bonusTodayMin
        compose.onNodeWithContentDescription("Open Maya", substring = true).performClick()
        compose.onNodeWithText("Maya Chen").assertIsDisplayed()
        compose.onNodeWithText("Give extra time").performClick()
        compose.onNodeWithText("Give +15 min").performClick()
        compose.waitForIdle()
        val after = vm.state.value.children.first { it.id == Seed.MAYA }.bonusTodayMin
        // Maya's cap has 30 min of room, so the full 15 lands.
        assertEquals(before + 15, after)
        scrollTo("Bonus history")
    }

    @Test fun demoSyncTurnsInWork() {
        val openBefore = vm.state.value.tasks.count { it.state == app.recess.core.TaskState.Assigned }
        compose.onNodeWithText("Sync Classroom (demo)").performClick()
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
        compose.waitForIdle()
        val s = vm.state.value
        assertTrue(s.tasks.count { it.state == app.recess.core.TaskState.Assigned } < openBefore)
        assertEquals(ActivityKind.Sync, s.activity.first().kind)
    }
}
