package io.github.akuraneee.reschools

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.akuraneee.reschools.core.FakeFetcher
import io.github.akuraneee.reschools.core.FakeSite
import io.github.akuraneee.reschools.core.MemoryStore
import io.github.akuraneee.reschools.core.Persist
import io.github.akuraneee.reschools.core.PupilSession
import io.github.akuraneee.reschools.core.Pupil
import io.github.akuraneee.reschools.core.TEST_PUPIL
import androidx.compose.foundation.layout.WindowInsets
import io.github.akuraneee.reschools.ui.MainScreen
import io.github.akuraneee.reschools.ui.diary.DiaryScreen
import io.github.akuraneee.reschools.ui.schedule.ScheduleScreen
import io.github.akuraneee.reschools.ui.settings.SettingsScreen
import io.github.akuraneee.reschools.ui.stats.StatsScreen
import io.github.akuraneee.reschools.ui.theme.ReSchoolsTheme
import io.github.akuraneee.reschools.ui.welcome.WelcomeScreen
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Рендерит экраны на выдуманном дневнике в PNG (app/build/screenshots) — чтобы увидеть интерфейс без телефона.
 * Заодно проверяет, что экраны вообще собираются и не падают в светлой/тёмной теме и на разных ширинах.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("Europe/Minsk")
    private val today = LocalDate.of(2026, 10, 20)

    private fun at(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant()

    /** Состояние после двух заходов: утром загрузили, вечером учителя выставили новые отметки. */
    private fun state(withNewMarks: Boolean = true, pupils: List<Pupil> = listOf(TEST_PUPIL)): UiState = runBlocking {
        val site = FakeSite(today)
        val store = MemoryStore()
        val morning = Clock.fixed(at("2026-10-20T08:00:00"), zone)
        PupilSession(TEST_PUPIL, FakeFetcher(site), Persist(store), morning, 0).refresh()
        if (withNewMarks) site.bump()
        val evening = Clock.fixed(at("2026-10-20T16:30:00"), zone)
        val session = PupilSession(TEST_PUPIL, FakeFetcher(site), Persist(store), evening, 0)
        session.refresh()
        UiState(
            ready = true,
            pupils = pupils,
            pupil = TEST_PUPIL,
            today = today,
            weeks = session.diary.weeks,
            lessons = session.diary.allLessons(),
            newMarks = session.newMarks(),
            baseline = session.visit.baseline,
            lastSync = session.lastSync,
        )
    }

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { ReSchoolsTheme(darkTheme = dark, dynamicColor = false, content = content) }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun homeDark() = shot("home-dark", dark = true) { MainScreen(state(), {}, { _, _ -> }, {}, {}, {}) }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun homeLight() = shot("home-light") { MainScreen(state(), {}, { _, _ -> }, {}, {}, {}) }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun homeFirstVisit() = shot("home-first-visit") { MainScreen(state(withNewMarks = false).copy(newMarks = null, baseline = null), {}, { _, _ -> }, {}, {}, {}) }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun diaryLight() = shot("diary-light") { DiaryScreen(state(), WindowInsets(0), { _, _ -> }, {}) }

    @Test @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun diaryDark() = shot("diary-dark", dark = true) { DiaryScreen(state(), WindowInsets(0), { _, _ -> }, {}) }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun statsLight() = shot("stats-light") { StatsScreen(state(), WindowInsets(0), {}, {}, {}) }

    @Test @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun statsDark() = shot("stats-dark", dark = true) { StatsScreen(state(), WindowInsets(0), {}, {}, {}) }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun scheduleLight() = shot("schedule-light") { ScheduleScreen(state(), WindowInsets(0), { _, _ -> }, {}) }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi", fontScale = 2f)
    fun homeBigFont() = shot("home-font200") { MainScreen(state(), {}, { _, _ -> }, {}, {}, {}) }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun settings() = shot("settings") {
        SettingsScreen(state(pupils = listOf(TEST_PUPIL, Pupil("7654321", TEST_PUPIL.origin, "Тестова Анна"))), "0.2.0", {}, {}, {}, {}, {}, {})
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun welcome() = shot("welcome") { WelcomeScreen(UiState(ready = true), {}) }

    @Test @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun homeTablet() = shot("home-tablet") { MainScreen(state(), {}, { _, _ -> }, {}, {}, {}) }
}
