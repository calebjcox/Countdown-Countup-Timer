package com.calebjcox.countdownwidgets.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.calebjcox.countdownwidgets.R
import com.calebjcox.countdownwidgets.core.Backdrop
import com.calebjcox.countdownwidgets.core.DurationMath
import com.calebjcox.countdownwidgets.core.LabelStyle
import com.calebjcox.countdownwidgets.core.Precision
import com.calebjcox.countdownwidgets.core.Rendering
import com.calebjcox.countdownwidgets.core.RowVisibility
import com.calebjcox.countdownwidgets.core.TextTheme
import com.calebjcox.countdownwidgets.core.TimeField
import com.calebjcox.countdownwidgets.core.TimerSpec
import com.calebjcox.countdownwidgets.data.Timer
import com.calebjcox.countdownwidgets.data.TimerStore
import com.calebjcox.countdownwidgets.databinding.ActivityEditTimerBinding
import com.calebjcox.countdownwidgets.widget.WidgetPalette
import com.calebjcox.countdownwidgets.widget.WidgetUpdater
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class EditTimerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditTimerBinding
    private lateinit var store: TimerStore
    private lateinit var chips: Map<TimeField, Chip>

    private var timerId: String? = null
    private var precision = Precision.DATE
    private var targetDate: LocalDate = LocalDate.now().plusDays(1)
    private var targetTime: LocalTime = LocalTime.of(9, 0)
    private val fields = linkedSetOf<TimeField>()

    // Appearance is held in these fields and never read back off the controls. A control
    // carries only what something assigned to it, so reading one for a timer that loaded
    // no saved value reads the layout's attribute instead of the real default. The
    // defaults come from Timer, which names them once.
    private var labelStyle = Timer.DEFAULT_LABEL_STYLE
    private var textTheme = Timer.DEFAULT_TEXT_THEME
    private var backdrop = Timer.DEFAULT_BACKDROP
    private var nameVisibility = Timer.DEFAULT_NAME_VISIBILITY
    private var targetVisibility = Timer.DEFAULT_TARGET_VISIBILITY
    private var wrapValue = Timer.DEFAULT_WRAP_VALUE

    /** Guards the chip and toggle listeners while [syncUi] writes their state. */
    private var syncing = false

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditTimerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = TimerStore(this)
        chips = mapOf(
            TimeField.YEAR to binding.unitYear,
            TimeField.MONTH to binding.unitMonth,
            TimeField.WEEK to binding.unitWeek,
            TimeField.DAY to binding.unitDay,
            TimeField.HOUR to binding.unitHour,
            TimeField.MINUTE to binding.unitMinute,
            TimeField.SECOND to binding.unitSecond,
        )

        timerId = intent.getStringExtra(EXTRA_TIMER_ID)
        val existing = store.timer(timerId)

        when {
            savedInstanceState != null -> restore(savedInstanceState)
            existing != null -> load(existing)
            else -> fields.addAll(TimerSpec.DEFAULT_DATE_FIELDS)
        }

        setUpToolbar(isExisting = existing != null)
        setUpListeners()
        refresh()
    }

    // ------------------------------------------------------------------ state

    private fun load(timer: Timer) {
        binding.name.setText(timer.name)
        precision = timer.spec.precision
        targetDate = timer.spec.target.toLocalDate()
        targetTime = timer.spec.target.toLocalTime()
        fields.addAll(timer.spec.orderedFields)
        labelStyle = timer.labelStyle
        textTheme = timer.textTheme
        backdrop = timer.backdrop
        nameVisibility = timer.nameVisibility
        targetVisibility = timer.targetVisibility
        wrapValue = timer.wrapValue
    }

    private fun restore(state: Bundle) {
        precision = Precision.valueOf(state.getString(STATE_PRECISION, Precision.DATE.name))
        targetDate = LocalDate.parse(state.getString(STATE_DATE))
        targetTime = LocalTime.parse(state.getString(STATE_TIME))
        state.getStringArrayList(STATE_FIELDS)?.forEach { fields.add(TimeField.valueOf(it)) }
        labelStyle = LabelStyle.valueOf(state.getString(STATE_LABEL_STYLE, labelStyle.name))
        textTheme = TextTheme.valueOf(state.getString(STATE_TEXT_THEME, textTheme.name))
        backdrop = Backdrop.valueOf(state.getString(STATE_BACKDROP, backdrop.name))
        nameVisibility =
            RowVisibility.valueOf(state.getString(STATE_NAME_VISIBILITY, nameVisibility.name))
        targetVisibility =
            RowVisibility.valueOf(state.getString(STATE_TARGET_VISIBILITY, targetVisibility.name))
        wrapValue = state.getBoolean(STATE_WRAP_VALUE, wrapValue)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PRECISION, precision.name)
        outState.putString(STATE_DATE, targetDate.toString())
        outState.putString(STATE_TIME, targetTime.toString())
        outState.putStringArrayList(STATE_FIELDS, ArrayList(fields.map { it.name }))
        outState.putString(STATE_LABEL_STYLE, labelStyle.name)
        outState.putString(STATE_TEXT_THEME, textTheme.name)
        outState.putString(STATE_BACKDROP, backdrop.name)
        outState.putString(STATE_NAME_VISIBILITY, nameVisibility.name)
        outState.putString(STATE_TARGET_VISIBILITY, targetVisibility.name)
        outState.putBoolean(STATE_WRAP_VALUE, wrapValue)
    }

    // -------------------------------------------------------------------- ui

    private fun setUpToolbar(isExisting: Boolean) {
        binding.toolbar.setTitle(
            if (isExisting) R.string.edit_timer_title else R.string.new_timer_title,
        )
        binding.toolbar.setNavigationOnClickListener { finish() }
        if (isExisting) {
            binding.toolbar.inflateMenu(R.menu.menu_edit_timer)
            binding.toolbar.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_delete) {
                    confirmDelete()
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun setUpListeners() {
        binding.precisionGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || syncing) return@addOnButtonCheckedListener
            precision =
                if (checkedId == R.id.precision_date) Precision.DATE else Precision.DATE_TIME
            // Asking for a time of day turns on the units that time of day exists to
            // express. Normalization strips the clock units on the way to a bare date,
            // so without this the way back leaves a timer that shows nothing finer than
            // days while claiming a target of 6:30pm. Only the switch itself does this —
            // a clock unit unchecked afterwards stays unchecked.
            if (precision == Precision.DATE_TIME) fields.addAll(TimerSpec.DEFAULT_CLOCK_FIELDS)
            refresh()
        }

        chips.values.forEach { chip ->
            chip.setOnCheckedChangeListener { _, _ ->
                if (!syncing) {
                    readChips()
                    refresh()
                }
            }
        }

        binding.nameVisibilityGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || syncing) return@addOnButtonCheckedListener
            nameVisibility = rowVisibilityOf(
                checkedId,
                always = R.id.name_always,
                never = R.id.name_never,
            )
            refresh()
        }

        binding.targetVisibilityGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || syncing) return@addOnButtonCheckedListener
            targetVisibility = rowVisibilityOf(
                checkedId,
                always = R.id.target_always,
                never = R.id.target_never,
            )
            refresh()
        }

        binding.backdropGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || syncing) return@addOnButtonCheckedListener
            backdrop = when (checkedId) {
                R.id.backdrop_scrim -> Backdrop.SCRIM
                R.id.backdrop_panel -> Backdrop.PANEL
                else -> Backdrop.NONE
            }
            refresh()
        }

        // A ChipGroup rather than a toggle group, so the callback shape differs: it
        // reports the whole selection rather than one button, and an empty one during
        // the group's own bookkeeping is not the user choosing nothing.
        binding.textThemeGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (syncing) return@setOnCheckedStateChangeListener
            textTheme = when (checkedIds.firstOrNull()) {
                R.id.text_theme_light -> TextTheme.LIGHT
                R.id.text_theme_dark -> TextTheme.DARK
                R.id.text_theme_white -> TextTheme.WHITE
                R.id.text_theme_black -> TextTheme.BLACK
                R.id.text_theme_auto -> TextTheme.AUTO
                else -> return@setOnCheckedStateChangeListener
            }
            refresh()
        }

        // No syncing guard, unlike the controls below: nothing writes this field back,
        // so the only thing that moves it is the user typing — or the framework
        // restoring it after a rotation, which happens once onCreate has run and is
        // exactly when the preview needs redrawing anyway.
        binding.name.doAfterTextChanged { refresh() }

        binding.date.setOnClickListener { showDatePicker() }
        binding.time.setOnClickListener { showTimePicker() }
        binding.longLabels.setOnCheckedChangeListener { _, isChecked ->
            if (syncing) return@setOnCheckedChangeListener
            labelStyle = if (isChecked) LabelStyle.LONG else LabelStyle.SHORT
            refresh()
        }
        // Nothing in the preview moves when this changes — the card is as wide as the
        // screen and a line only breaks where there is no width for it — but it is
        // still read back through refresh() so every switch here behaves the same way.
        binding.wrapValue.setOnCheckedChangeListener { _, isChecked ->
            if (syncing) return@setOnCheckedChangeListener
            wrapValue = isChecked
            refresh()
        }
        binding.save.setOnClickListener { save() }
    }

    /**
     * Which of a row group's three buttons is checked. Reading takes only the two ends:
     * a group with one selection required has no fourth answer, so "neither end" is the
     * middle. [rowButtonFor] going the other way does need all three, because it has to
     * name the button to check.
     */
    private fun rowVisibilityOf(checkedId: Int, always: Int, never: Int): RowVisibility =
        when (checkedId) {
            always -> RowVisibility.ALWAYS
            never -> RowVisibility.NEVER
            else -> RowVisibility.WHEN_ROOM
        }

    /** The other direction, for [syncUi]. */
    private fun rowButtonFor(
        visibility: RowVisibility,
        always: Int,
        whenRoom: Int,
        never: Int,
    ): Int = when (visibility) {
        RowVisibility.ALWAYS -> always
        RowVisibility.WHEN_ROOM -> whenRoom
        RowVisibility.NEVER -> never
    }

    private fun readChips() {
        fields.clear()
        chips.forEach { (field, chip) -> if (chip.isChecked) fields.add(field) }
    }

    /**
     * Runs everything the user just changed back through [TimerSpec.of] and redraws
     * from the result. Normalization is therefore the only place the rules about
     * which units may appear together live — the chips show what the widget will
     * actually do, including units that were filled in on the user's behalf.
     */
    private fun refresh() {
        val spec = TimerSpec.of(targetDateTime(), precision, fields)
        fields.clear()
        fields.addAll(spec.orderedFields)
        syncUi(spec)
    }

    private fun syncUi(spec: TimerSpec) {
        syncing = true
        chips.forEach { (field, chip) -> chip.isChecked = field in spec.fields }
        binding.precisionGroup.check(
            if (precision == Precision.DATE) R.id.precision_date else R.id.precision_date_time,
        )
        binding.longLabels.isChecked = labelStyle == LabelStyle.LONG
        binding.wrapValue.isChecked = wrapValue
        binding.nameVisibilityGroup.check(
            rowButtonFor(nameVisibility, R.id.name_always, R.id.name_when_room, R.id.name_never),
        )
        binding.targetVisibilityGroup.check(
            rowButtonFor(
                targetVisibility,
                R.id.target_always,
                R.id.target_when_room,
                R.id.target_never,
            ),
        )
        binding.backdropGroup.check(
            when (backdrop) {
                Backdrop.NONE -> R.id.backdrop_none
                Backdrop.SCRIM -> R.id.backdrop_scrim
                Backdrop.PANEL -> R.id.backdrop_panel
            },
        )
        binding.textThemeGroup.check(
            when (textTheme) {
                TextTheme.AUTO -> R.id.text_theme_auto
                TextTheme.LIGHT -> R.id.text_theme_light
                TextTheme.DARK -> R.id.text_theme_dark
                TextTheme.WHITE -> R.id.text_theme_white
                TextTheme.BLACK -> R.id.text_theme_black
            },
        )
        syncing = false

        val showTime = precision == Precision.DATE_TIME
        binding.clockUnits.visibility = if (showTime) View.VISIBLE else View.GONE
        binding.time.visibility = if (showTime) View.VISIBLE else View.GONE

        // On its own panel the text and the panel are a matched pair, so offering a
        // choice there would only manufacture unreadable combinations. A scrim is not
        // that: it is drawn from whatever tone is chosen, so the choice still means
        // something and is still worth showing.
        val overWallpaper = backdrop != Backdrop.PANEL
        binding.textThemeLabel.visibility = if (overWallpaper) View.VISIBLE else View.GONE
        binding.textThemeGroup.visibility = if (overWallpaper) View.VISIBLE else View.GONE
        binding.textThemeHint.visibility = if (overWallpaper) View.VISIBLE else View.GONE

        binding.date.text = targetDate.format(dateFormatter)
        binding.time.text = targetTime.format(timeFormatter)

        val display = DurationMath.compute(
            System.currentTimeMillis(),
            ZoneId.systemDefault(),
            spec,
        )
        // Switched off, or blank, leaves the row out rather than leaving a gap — both
        // of which are what the widget itself does with that row; see WidgetRenderer.
        //
        // The card is as wide as the screen and sized by its content, so it has room for
        // every row and ALWAYS and WHEN_ROOM look the same here. Only NEVER can change
        // what the preview shows, which is the honest answer: the difference between the
        // other two is a property of the cell, and there is no cell on this screen.
        val name = enteredName()
        binding.previewName.text = name
        binding.previewName.visibility =
            if (nameVisibility != RowVisibility.NEVER && name.isNotEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        binding.previewValue.text = Rendering.formatDisplay(display, labelStyle)
        binding.previewFooter.text = TimerSummary.target(this, spec)
        binding.previewFooter.visibility =
            if (targetVisibility != RowVisibility.NEVER) View.VISIBLE else View.GONE
        syncPreviewColors()
    }

    /**
     * Shows the colours the widget will actually use. A transparent widget is
     * previewed over a flat mid-tone rather than the app's own surface: light text
     * on a light card would look broken here while being exactly right on the
     * wallpaper, and the point of the preview is to judge that choice.
     *
     * A scrim is blended into that stand-in rather than layered over it in a view of its
     * own. The result is the same colour the text will really sit on — compositing is
     * what the framework would be doing anyway — and it keeps the preview one background,
     * so the card's corners stay the card's.
     */
    private fun syncPreviewColors() {
        val colors = WidgetPalette.forAppearance(this, backdrop, textTheme)
        binding.previewName.setTextColor(colors.secondary)
        binding.previewValue.setTextColor(colors.primary)
        binding.previewFooter.setTextColor(colors.secondary)

        val behind = ContextCompat.getColor(
            this,
            if (backdrop == Backdrop.PANEL) {
                R.color.widget_background
            } else {
                R.color.preview_wallpaper_stand_in
            },
        )
        val scrim = WidgetPalette.scrimColor(this, backdrop, textTheme)
        binding.previewCard.setCardBackgroundColor(
            if (scrim == null) behind else ColorUtils.compositeColors(scrim, behind),
        )
    }

    /** What [save] would store as the name, so the preview cannot promise another. */
    private fun enteredName(): String = binding.name.text?.toString()?.trim().orEmpty()

    private fun targetDateTime(): LocalDateTime = LocalDateTime.of(
        targetDate,
        if (precision == Precision.DATE) LocalTime.MIDNIGHT else targetTime,
    )

    // --------------------------------------------------------------- pickers

    override fun onStart() {
        super.onStart()
        // A picker shown before a rotation is restored by the fragment manager with
        // its listener gone; re-attach so a selection made after rotating still lands.
        @Suppress("UNCHECKED_CAST")
        (supportFragmentManager.findFragmentByTag(TAG_DATE) as? MaterialDatePicker<Long>)
            ?.let(::bindDatePicker)
        (supportFragmentManager.findFragmentByTag(TAG_TIME) as? MaterialTimePicker)
            ?.let(::bindTimePicker)
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            // MaterialDatePicker speaks in UTC midnight millis, whatever the device
            // zone is; converting through anything else shifts the date by a day.
            .setSelection(targetDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .build()
        bindDatePicker(picker)
        picker.show(supportFragmentManager, TAG_DATE)
    }

    private fun bindDatePicker(picker: MaterialDatePicker<Long>) {
        picker.clearOnPositiveButtonClickListeners()
        picker.addOnPositiveButtonClickListener { selection ->
            targetDate = Instant.ofEpochMilli(selection).atZone(ZoneOffset.UTC).toLocalDate()
            refresh()
        }
    }

    private fun showTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(
                if (DateFormat.is24HourFormat(this)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H,
            )
            .setHour(targetTime.hour)
            .setMinute(targetTime.minute)
            .build()
        bindTimePicker(picker)
        picker.show(supportFragmentManager, TAG_TIME)
    }

    private fun bindTimePicker(picker: MaterialTimePicker) {
        picker.clearOnPositiveButtonClickListeners()
        picker.addOnPositiveButtonClickListener {
            targetTime = LocalTime.of(picker.hour, picker.minute)
            refresh()
        }
    }

    // ---------------------------------------------------------------- actions

    private fun save() {
        val name = enteredName()
        if (name.isEmpty()) {
            binding.nameLayout.error = getString(R.string.name_required)
            return
        }
        binding.nameLayout.error = null

        val id = timerId ?: Timer.newId()
        store.save(
            Timer(
                id = id,
                name = name,
                spec = TimerSpec.of(targetDateTime(), precision, fields),
                labelStyle = labelStyle,
                textTheme = textTheme,
                backdrop = backdrop,
                nameVisibility = nameVisibility,
                targetVisibility = targetVisibility,
                wrapValue = wrapValue,
            ),
        )
        WidgetUpdater.updateForTimer(this, id)

        setResult(RESULT_OK, Intent().putExtra(EXTRA_TIMER_ID, id))
        finish()
    }

    private fun confirmDelete() {
        val id = timerId ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_timer_title)
            .setMessage(R.string.delete_timer_body)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                store.delete(id)
                // Widgets that pointed here fall back to their "choose a timer" state.
                WidgetUpdater.updateAll(this)
                finish()
            }
            .show()
    }

    companion object {
        const val EXTRA_TIMER_ID = "timerId"

        private const val TAG_DATE = "date"
        private const val TAG_TIME = "time"
        private const val STATE_PRECISION = "precision"
        private const val STATE_DATE = "date"
        private const val STATE_TIME = "time"
        private const val STATE_FIELDS = "fields"
        private const val STATE_LABEL_STYLE = "labelStyle"
        private const val STATE_TEXT_THEME = "textTheme"
        private const val STATE_BACKDROP = "backdrop"
        private const val STATE_NAME_VISIBILITY = "nameVisibility"
        private const val STATE_TARGET_VISIBILITY = "targetVisibility"
        private const val STATE_WRAP_VALUE = "wrapValue"

        fun editIntent(context: Context, timerId: String?): Intent =
            Intent(context, EditTimerActivity::class.java)
                .putExtra(EXTRA_TIMER_ID, timerId)

        /**
         * Tapping a widget opens its timer for editing. The request code is the
         * widget id: `PendingIntent` equality ignores extras, so without a distinct
         * request code per widget every widget would end up sharing one intent.
         */
        fun widgetTapIntent(context: Context, appWidgetId: Int, timerId: String): PendingIntent =
            PendingIntent.getActivity(
                context,
                appWidgetId,
                editIntent(context, timerId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
