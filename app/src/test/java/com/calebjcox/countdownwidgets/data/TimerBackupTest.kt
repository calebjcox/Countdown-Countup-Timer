package com.calebjcox.countdownwidgets.data

import com.calebjcox.countdownwidgets.core.LabelStyle
import com.calebjcox.countdownwidgets.core.Precision
import com.calebjcox.countdownwidgets.core.TextTheme
import com.calebjcox.countdownwidgets.core.TimeField
import com.calebjcox.countdownwidgets.core.TimerSpec
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The backup format is the one piece of this app whose bugs are permanent: a
 * timer that fails to come back is not recoverable from anywhere else.
 */
class TimerBackupTest {

    @Test
    fun `a backup round trips every field of every timer`() {
        val timers = listOf(
            timer(
                id = "christmas",
                name = "Christmas",
                target = LocalDateTime.parse("2026-12-25T00:00"),
                precision = Precision.DATE,
                fields = setOf(TimeField.MONTH, TimeField.DAY),
                labelStyle = LabelStyle.LONG,
                textTheme = TextTheme.DARK,
                showBackground = true,
                showName = false,
                showTarget = true,
            ),
            timer(
                id = "launch",
                name = "Launch",
                target = LocalDateTime.parse("2027-03-01T09:30"),
                precision = Precision.DATE_TIME,
                fields = setOf(TimeField.DAY, TimeField.HOUR, TimeField.MINUTE),
                labelStyle = LabelStyle.SHORT,
                textTheme = TextTheme.LIGHT,
                showBackground = false,
                showName = true,
                showTarget = false,
            ),
        )

        assertEquals(timers, decoded(TimerBackup.encode(timers)))
    }

    @Test
    fun `an exported file names the format and its version`() {
        val encoded = TimerBackup.encode(listOf(timer(id = "one")))

        assertTrue(encoded, encoded.contains("\"format\": \"countdowns-backup\""))
        assertTrue(encoded, encoded.contains("\"version\": 1"))
    }

    @Test
    fun `a bare array of timers is accepted`() {
        // What the app stores internally, and so the shape someone assembling a
        // file by hand is most likely to produce.
        val bare = JSONArray()
            .put(timer(id = "one").toJson())
            .put(timer(id = "two").toJson())
            .toString()

        assertEquals(listOf("one", "two"), decoded(bare).map { it.id })
    }

    @Test
    fun `text that is not json is not a backup`() {
        assertEquals(TimerBackup.Result.NotABackup, TimerBackup.decode("hello, world"))
        assertEquals(TimerBackup.Result.NotABackup, TimerBackup.decode(""))
        assertEquals(TimerBackup.Result.NotABackup, TimerBackup.decode("{ truncated"))
    }

    @Test
    fun `json from somewhere else is not a backup`() {
        assertEquals(
            TimerBackup.Result.NotABackup,
            TimerBackup.decode("""{"format":"someone-elses-app","timers":[]}"""),
        )
    }

    @Test
    fun `a backup from a newer version reports its version rather than failing`() {
        val result = TimerBackup.decode(
            """{"format":"countdowns-backup","version":99,"timers":[]}""",
        )

        assertEquals(TimerBackup.Result.TooNew(99), result)
    }

    @Test
    fun `one unreadable entry does not cost the timers either side of it`() {
        val good = TimerBackup.encode(listOf(timer(id = "before"), timer(id = "after")))
        val broken = good.replaceFirst("\"precision\": \"DATE\"", "\"precision\": \"FORTNIGHTS\"")

        assertEquals(listOf("after"), decoded(broken).map { it.id })
    }

    @Test
    fun `a repeated id survives only once`() {
        val duplicated = TimerBackup.encode(
            listOf(timer(id = "same", name = "First"), timer(id = "same", name = "Second")),
        )

        val timers = decoded(duplicated)
        assertEquals(1, timers.size)
        assertEquals("Second", timers.single().name)
    }

    @Test
    fun `an entry with no id is dropped rather than colliding`() {
        val decoded = TimerBackup.decode(
            """[{"id":"","name":"Nameless","target":"2026-12-25T00:00:00",
               "precision":"DATE","fields":["DAY"]}]""",
        )

        assertEquals(emptyList<Timer>(), (decoded as TimerBackup.Result.Ok).timers)
    }

    @Test
    fun `a backup made before the row toggles existed keeps both rows`() {
        // The keys are simply absent from such a file, and absent has to mean what the
        // timer was doing when it was exported: showing its name and its target date.
        val decoded = TimerBackup.decode(
            """[{"id":"christmas","name":"Christmas","target":"2026-12-25T00:00:00",
               "precision":"DATE","fields":["DAY"]}]""",
        )

        val timer = (decoded as TimerBackup.Result.Ok).timers.single()
        assertTrue(timer.showName)
        assertTrue(timer.showTarget)
    }

    @Test
    fun `the suggested file name carries the date`() {
        assertEquals(
            "countdowns-backup-2026-08-09.json",
            TimerBackup.defaultFileName(LocalDate.parse("2026-08-09")),
        )
    }

    private fun decoded(text: String): List<Timer> =
        (TimerBackup.decode(text) as TimerBackup.Result.Ok).timers

    private fun timer(
        id: String,
        name: String = "Christmas",
        target: LocalDateTime = LocalDateTime.parse("2026-12-25T00:00"),
        precision: Precision = Precision.DATE,
        fields: Set<TimeField> = setOf(TimeField.DAY),
        labelStyle: LabelStyle = Timer.DEFAULT_LABEL_STYLE,
        textTheme: TextTheme = Timer.DEFAULT_TEXT_THEME,
        showBackground: Boolean = Timer.DEFAULT_SHOW_BACKGROUND,
        showName: Boolean = Timer.DEFAULT_SHOW_NAME,
        showTarget: Boolean = Timer.DEFAULT_SHOW_TARGET,
    ) = Timer(
        id = id,
        name = name,
        spec = TimerSpec.of(target, precision, fields),
        labelStyle = labelStyle,
        textTheme = textTheme,
        showBackground = showBackground,
        showName = showName,
        showTarget = showTarget,
    )
}
