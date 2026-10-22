package com.karas.preseteditor

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
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

    @Test
    fun createPresetAndAddPrompt() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntilAtLeastOneExists(hasText("Новый пресет"), 10_000)
        compose.onNodeWithText("Новый пресет").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Промпты"), 10_000)
        compose.onNodeWithText("Промпт").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Название"), 10_000)
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
        compose.waitUntilAtLeastOneExists(hasText("Промпты"), 10_000)
        compose.waitUntilAtLeastOneExists(hasText("Main Prompt"), 10_000)
        compose.onNodeWithText("Инфо").performClick()
        compose.waitForIdle()
    }
}
