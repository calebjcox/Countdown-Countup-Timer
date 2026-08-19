package com.calebjcox.countdownwidgets.data

import com.calebjcox.countdownwidgets.core.Backdrop
import com.calebjcox.countdownwidgets.core.LabelStyle
import com.calebjcox.countdownwidgets.core.Precision
import com.calebjcox.countdownwidgets.core.RowVisibility
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
                backdrop = Backdrop.PANEL,
                nameVisibility = RowVisibility.NEVER,
                targetVisibility = RowVisibility.WHEN_ROOM,
                wrapValue = false,
            ),
            timer(
                id = "launch",
                name = "Launch",
                target = LocalDateTime.parse("2027-03-01T09:30"),
                precision = Precision.DATE_TIME,
                fields = setOf(TimeField.DAY, TimeField.HOUR, TimeField.MINUTE),
                labelStyle = LabelStyle.SHORT,
                textTheme = TextTheme.WHITE,
                backdrop = Backdrop.SCRIM,
                nameVisibility = RowVisibility.ALWAYS,
                targetVisibility = RowVisibility.NEVER,
                wrapValue = true,
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
        assertEquals(RowVisibility.WHEN_ROOM, timer.nameVisibility)
        assertEquals(RowVisibility.WHEN_ROOM, timer.targetVisibility)
        assertEquals(Backdrop.NONE, timer.backdrop)
    }

    @Test
    fun `a backup written while the three settings were booleans still says what it meant`() {
        // WHEN_ROOM rather than ALWAYS for the row that was on, which is the whole of the
        // migration: `true` never meant "at every size", it meant "wherever it fits", and
        // reading it as always would print a name onto every 1x1 whose owner never asked
        // for one. The row that was off has only one thing it can mean.
        val decoded = TimerBackup.decode(
            """[{"id":"christmas","name":"Christmas","target":"2026-12-25T00:00:00",
               "precision":"DATE","fields":["DAY"],
               "showName":true,"showTarget":false,"showBackground":true}]""",
        )

        val timer = (decoded as TimerBackup.Result.Ok).timers.single()
        assertEquals(RowVisibility.WHEN_ROOM, timer.nameVisibility)
        assertEquals(RowVisibility.NEVER, timer.targetVisibility)
        assertEquals(Backdrop.PANEL, timer.backdrop)
    }

    @Test
    fun `the enum wins where a file carries both spellings`() {
        // Only a hand-edited file can be in this state, but it has an answer: the enum is
        // the one that can express all three values, so it is the one that is read.
        val decoded = TimerBackup.decode(
            """[{"id":"christmas","name":"Christmas","target":"2026-12-25T00:00:00",
               "precision":"DATE","fields":["DAY"],
               "showName":false,"nameVisibility":"ALWAYS",
               "showBackground":true,"backdrop":"SCRIM"}]""",
        )

        val timer = (decoded as TimerBackup.Result.Ok).timers.single()
        assertEquals(RowVisibility.ALWAYS, timer.nameVisibility)
        assertEquals(Backdrop.SCRIM, timer.backdrop)
    }

    @Test
    fun `a backup made before wrapping existed is allowed to wrap`() {
        // The opposite of what the test above wants, and the pair is the point. A missing
        // row toggle has to mean "as it was" or an old file would delete a row someone
        // is looking at; a missing wrap cannot delete anything, so it means what it means
        // for a timer made today.
        val decoded = TimerBackup.decode(
            """[{"id":"christmas","name":"Christmas","target":"2026-12-25T00:00:00",
               "precision":"DATE","fields":["DAY"]}]""",
        )

        assertTrue((decoded as TimerBackup.Result.Ok).timers.single().wrapValue)
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
        backdrop: Backdrop = Timer.DEFAULT_BACKDROP,
        nameVisibility: RowVisibility = Timer.DEFAULT_NAME_VISIBILITY,
        targetVisibility: RowVisibility = Timer.DEFAULT_TARGET_VISIBILITY,
        wrapValue: Boolean = Timer.DEFAULT_WRAP_VALUE,
    ) = Timer(
        id = id,
        name = name,
        spec = TimerSpec.of(target, precision, fields),
        labelStyle = labelStyle,
        textTheme = textTheme,
        backdrop = backdrop,
        nameVisibility = nameVisibility,
        targetVisibility = targetVisibility,
        wrapValue = wrapValue,
    )
}
