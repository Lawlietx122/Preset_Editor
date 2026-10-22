package com.karas.preseteditor

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opens the main screens on a real device, so crashes show up in CI before a release. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    /** Waits for [text]; on timeout the failure message carries what the screen showed. */
    private fun waitFor(text: String) {
        try {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: Throwable) {
            val tree = runCatching { compose.onRoot(useUnmergedTree = true).printToString() }
                .getOrElse { "no compose root: $it" }
            throw AssertionError("«$text» not shown. Screen:\n$tree", error)
        }
    }

    @Test
    fun createPresetAndAddPrompt() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor("Новый пресет")
        compose.onAllNodesWithText("Новый пресет", useUnmergedTree = true)[0].performClick()
        waitFor("Промпты")
        compose.onAllNodesWithText("Промпт", useUnmergedTree = true)[0].performClick()
        waitFor("Название")
        compose.waitForIdle()
    }

    @Test
    fun importSharedPreset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val json = InstrumentationRegistry.getInstrumentation().context.assets.open("sample-preset.json").bufferedReader().readText()
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, json)
        }
        scenario = ActivityScenario.launch(intent)
        waitFor("Промпты")
        waitFor("Main Prompt")
        compose.onAllNodesWithText("Инфо", useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()
    }
}
