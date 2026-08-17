package com.calebjcox.countdownwidgets.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.calebjcox.countdownwidgets.R
import com.calebjcox.countdownwidgets.core.DurationMath
import com.calebjcox.countdownwidgets.core.LabelStyle
import com.calebjcox.countdownwidgets.core.Precision
import com.calebjcox.countdownwidgets.core.Rendering
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

    // Appearance is held here rather than read back off the switches. Doing it the
    // other way round is what let a new timer save showBackground = false: the
    // switch was only ever assigned when loading an existing timer, so a new one
    // silently inherited whatever the layout happened to default to.
    private var labelStyle = Timer.DEFAULT_LABEL_STYLE
    private var textTheme = Timer.DEFAULT_TEXT_THEME
    private var showBackground = Timer.DEFAULT_SHOW_BACKGROUND

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
        showBackground = timer.showBackground
    }

    private fun restore(state: Bundle) {
        precision = Precision.valueOf(state.getString(STATE_PRECISION, Precision.DATE.name))
        targetDate = LocalDate.parse(state.getString(STATE_DATE))
        targetTime = LocalTime.parse(state.getString(STATE_TIME))
        state.getStringArrayList(STATE_FIELDS)?.forEach { fields.add(TimeField.valueOf(it)) }
        labelStyle = LabelStyle.valueOf(state.getString(STATE_LABEL_STYLE, labelStyle.name))
        textTheme = TextTheme.valueOf(state.getString(STATE_TEXT_THEME, textTheme.name))
        showBackground = state.getBoolean(STATE_SHOW_BACKGROUND, showBackground)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PRECISION, precision.name)
        outState.putString(STATE_DATE, targetDate.toString())
        outState.putString(STATE_TIME, targetTime.toString())
        outState.putStringArrayList(STATE_FIELDS, ArrayList(fields.map { it.name }))
        outState.putString(STATE_LABEL_STYLE, labelStyle.name)
        outState.putString(STATE_TEXT_THEME, textTheme.name)
        outState.putBoolean(STATE_SHOW_BACKGROUND, showBackground)
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

        binding.textThemeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || syncing) return@addOnButtonCheckedListener
            textTheme = when (checkedId) {
                R.id.text_theme_light -> TextTheme.LIGHT
                R.id.text_theme_dark -> TextTheme.DARK
                else -> TextTheme.AUTO
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
        binding.showBackground.setOnCheckedChangeListener { _, isChecked ->
            if (syncing) return@setOnCheckedChangeListener
            showBackground = isChecked
            refresh()
        }
        binding.save.setOnClickListener { save() }
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
        binding.showBackground.isChecked = showBackground
        binding.textThemeGroup.check(
            when (textTheme) {
                TextTheme.AUTO -> R.id.text_theme_auto
                TextTheme.LIGHT -> R.id.text_theme_light
                TextTheme.DARK -> R.id.text_theme_dark
            },
        )
        syncing = false

        val showTime = precision == Precision.DATE_TIME
        binding.clockUnits.visibility = if (showTime) View.VISIBLE else View.GONE
        binding.time.visibility = if (showTime) View.VISIBLE else View.GONE

        // On its own panel the text and the panel are a matched pair, so offering a
        // choice there would only manufacture unreadable combinations.
        val onWallpaper = !showBackground
        binding.textThemeLabel.visibility = if (onWallpaper) View.VISIBLE else View.GONE
        binding.textThemeGroup.visibility = if (onWallpaper) View.VISIBLE else View.GONE

        binding.date.text = targetDate.format(dateFormatter)
        binding.time.text = targetTime.format(timeFormatter)

        val display = DurationMath.compute(
            System.currentTimeMillis(),
            ZoneId.systemDefault(),
            spec,
        )
        // Blank leaves the row out rather than leaving a gap, which is what the widget
        // does with a nameless timer — and while the field is empty it is what the
        // widget for this timer would show.
        val name = enteredName()
        binding.previewName.text = name
        binding.previewName.visibility = if (name.isEmpty()) View.GONE else View.VISIBLE
        binding.previewValue.text = Rendering.formatDisplay(display, labelStyle)
        binding.previewFooter.text = TimerSummary.target(this, spec)
        syncPreviewColors(onWallpaper)
    }

    /**
     * Shows the colours the widget will actually use. A transparent widget is
     * previewed over a flat mid-tone rather than the app's own surface: light text
     * on a light card would look broken here while being exactly right on the
     * wallpaper, and the point of the preview is to judge that choice.
     */
    private fun syncPreviewColors(onWallpaper: Boolean) {
        val colors = WidgetPalette.forAppearance(this, showBackground, textTheme)
        binding.previewName.setTextColor(colors.secondary)
        binding.previewValue.setTextColor(colors.primary)
        binding.previewFooter.setTextColor(colors.secondary)
        binding.previewCard.setCardBackgroundColor(
            ContextCompat.getColor(
                this,
                if (onWallpaper) R.color.preview_wallpaper_stand_in else R.color.widget_background,
            ),
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
                showBackground = showBackground,
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
        private const val STATE_SHOW_BACKGROUND = "showBackground"

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
