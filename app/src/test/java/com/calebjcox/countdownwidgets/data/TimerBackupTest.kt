package com.calebjcox.countdownwidgets.data

import com.calebjcox.countdownwidgets.core.Backdrop
import com.calebjcox.countdownwidgets.core.LabelStyle
import com.calebjcox.countdownwidgets.core.Precision
import com.calebjcox.countdownwidgets.core.RowVisibility
import com.calebjcox.countdownwidgets.core.ScrimOpacity
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
                scrimOpacity = ScrimOpacity.MIN,
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
                scrimOpacity = 40,
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
    fun `a backup made before the tint had an opacity is read at today's default`() {
        // The one place the rule beside it is deliberately not followed. Everything else
        // here reads absent as what the widget was already doing; there is no such value
        // to read for this one, because the wash came in two — 65% under light text and
        // 75% under dark — and a single dial covering both is the trade this app has
        // chosen. So an old Tinted timer with dark text does thin slightly on upgrade.
        // That is a change its owner can see and can undo with the dial, which is what
        // makes it affordable. See the discussion on #28.
        val decoded = TimerBackup.decode(
            """[{"id":"christmas","name":"Christmas","target":"2026-12-25T00:00:00",
               "precision":"DATE","fields":["DAY"],"backdrop":"SCRIM"}]""",
        )

        val timer = (decoded as TimerBackup.Result.Ok).timers.single()
        assertEquals(ScrimOpacity.DEFAULT, timer.scrimOpacity)
    }

    @Test
    fun `an opacity the dial cannot stop on is read as the nearest one it can`() {
        // The one setting that is a number, so the one whose stored value can be wrong
        // rather than merely unrecognised: an enum nobody can spell falls back to a
        // default, and 4000 would otherwise be handed to the widget as written.
        val decoded = TimerBackup.decode(
            """[{"id":"low","name":"Low","target":"2026-12-25T00:00:00",
               "precision":"DATE","fields":["DAY"],"backdrop":"SCRIM","scrimOpacity":-5},
              {"id":"odd","name":"Odd","target":"2026-12-25T00:00:00",
               "precision":"DATE","fields":["DAY"],"backdrop":"SCRIM","scrimOpacity":43},
              {"id":"high","name":"High","target":"2026-12-25T00:00:00",
               "precision":"DATE","fields":["DAY"],"backdrop":"SCRIM","scrimOpacity":4000}]""",
        )

        val timers = (decoded as TimerBackup.Result.Ok).timers
        assertEquals(
            listOf(ScrimOpacity.MIN, 45, ScrimOpacity.MAX),
            timers.map { it.scrimOpacity },
        )
    }

    @Test
    fun `an opacity that is not a number at all reads as the default`() {
        // The other way this field can be wrong, and the one a person makes rather than a
        // program: a percent sign left on, a null where a number was deleted, the wrong
        // type entirely. Hand-edited backups are the reason this field is guarded at all
        // — see ScrimOpacity — and the test beside this one only covers values that are
        // already numbers.
        //
        // Degrading to the default is what every enum here does. It matters more for this
        // one because the wrong answer would be silent: the timer keeps Backdrop.SCRIM,
        // so 0% is a Tinted widget drawing no wash, with no shadow to stand in for it.
        val decoded = TimerBackup.decode(
            """[{"id":"percent","name":"Percent","target":"2026-12-25T00:00:00",
               "precision":"DATE","fields":["DAY"],"backdrop":"SCRIM","scrimOpacity":"70%"},
              {"id":"null","name":"Null","target":"2026-12-25T00:00:00",
               "precision":"DATE","fields":["DAY"],"backdrop":"SCRIM","scrimOpacity":null},
              {"id":"boolean","name":"Boolean","target":"2026-12-25T00:00:00",
               "precision":"DATE","fields":["DAY"],"backdrop":"SCRIM","scrimOpacity":true}]""",
        )

        val timers = (decoded as TimerBackup.Result.Ok).timers
        assertEquals(
            listOf(ScrimOpacity.DEFAULT, ScrimOpacity.DEFAULT, ScrimOpacity.DEFAULT),
            timers.map { it.scrimOpacity },
        )
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
        scrimOpacity: Int = Timer.DEFAULT_SCRIM_OPACITY,
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
        scrimOpacity = scrimOpacity,
        nameVisibility = nameVisibility,
        targetVisibility = targetVisibility,
        wrapValue = wrapValue,
    )
}
