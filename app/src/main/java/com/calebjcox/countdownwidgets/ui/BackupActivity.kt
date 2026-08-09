package com.calebjcox.countdownwidgets.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.calebjcox.countdownwidgets.R
import com.calebjcox.countdownwidgets.data.Timer
import com.calebjcox.countdownwidgets.data.TimerBackup
import com.calebjcox.countdownwidgets.data.TimerStore
import com.calebjcox.countdownwidgets.databinding.ActivityBackupBinding
import com.calebjcox.countdownwidgets.widget.WidgetUpdater
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * Exporting and importing timers as a file, plus an explanation of what the
 * phone's own Google backup already does.
 *
 * Both directions go through the Storage Access Framework, so the user picks the
 * file themselves and the app needs no storage permission — the manifest still
 * declares exactly one permission, and there is nothing here to opt into.
 */
class BackupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackupBinding
    private lateinit var store: TimerStore

    /**
     * File I/O runs here rather than on the main thread. The files are a few
     * kilobytes, but a picked `content://` Uri can belong to a cloud provider that
     * blocks while it fetches. A bare executor keeps the project's no-extra-
     * dependency rule: coroutines are not a declared dependency of this module.
     */
    private val io = Executors.newSingleThreadExecutor()

    private val exportFile =
        registerForActivityResult(ActivityResultContracts.CreateDocument(TimerBackup.MIME_TYPE)) { uri ->
            uri?.let(::export)
        }

    private val importFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(::importFrom)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = TimerStore(this)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.export.setOnClickListener {
            if (store.timers().isEmpty()) {
                // Writing an empty file is never what was meant, and if the user
                // aimed at an existing backup it would destroy it.
                say(getString(R.string.nothing_to_export))
            } else {
                exportFile.launch(TimerBackup.defaultFileName(LocalDate.now()))
            }
        }

        binding.importTimers.setOnClickListener {
            importFile.launch(TimerBackup.IMPORT_MIME_TYPES)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        io.shutdown()
    }

    // ---------------------------------------------------------------- exporting

    private fun export(uri: Uri) {
        val timers = store.timers()
        io.execute {
            val written = runCatching {
                // "wt" truncates. Without it, saving over a longer backup leaves the
                // tail of the old one behind and the file no longer parses.
                contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                    stream.write(TimerBackup.encode(timers).toByteArray())
                } ?: error("no output stream for $uri")
            }.isSuccess

            onUi {
                if (written) {
                    say(resources.getQuantityString(R.plurals.exported_timers, timers.size, timers.size))
                } else {
                    say(getString(R.string.export_failed))
                }
            }
        }
    }

    // ---------------------------------------------------------------- importing

    private fun importFrom(uri: Uri) {
        io.execute {
            val text = runCatching { read(uri) }.getOrNull()
            onUi {
                if (text == null) {
                    say(getString(R.string.import_read_failed))
                    return@onUi
                }
                when (val result = TimerBackup.decode(text)) {
                    is TimerBackup.Result.TooNew -> say(getString(R.string.import_too_new))
                    TimerBackup.Result.NotABackup -> say(getString(R.string.import_not_a_backup))
                    is TimerBackup.Result.Ok ->
                        if (result.timers.isEmpty()) {
                            say(getString(R.string.import_empty))
                        } else {
                            askHowToImport(result.timers)
                        }
                }
            }
        }
    }

    /**
     * Reads the picked file, refusing anything implausibly large. The picker will
     * happily hand back any file on the device; a real backup is a few kilobytes.
     */
    private fun read(uri: Uri): String {
        val stream = contentResolver.openInputStream(uri) ?: error("no input stream for $uri")
        return stream.use {
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8 * 1024)
            while (true) {
                val count = it.read(chunk)
                if (count < 0) break
                buffer.write(chunk, 0, count)
                if (buffer.size() > MAX_IMPORT_BYTES) error("$uri is too large to be a backup")
            }
            String(buffer.toByteArray(), Charsets.UTF_8)
        }
    }

    private fun askHowToImport(timers: List<Timer>) {
        MaterialAlertDialogBuilder(this)
            .setTitle(resources.getQuantityString(R.plurals.import_title, timers.size, timers.size))
            .setMessage(R.string.import_body)
            .setPositiveButton(R.string.import_merge) { _, _ -> merge(timers) }
            .setNeutralButton(R.string.import_replace) { _, _ -> confirmReplace(timers) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun merge(timers: List<Timer>) {
        val result = store.merge(timers)
        applied()
        say(getString(R.string.imported_merged, result.added, result.updated))
    }

    /** Only worth a second question when replacing would actually lose something. */
    private fun confirmReplace(timers: List<Timer>) {
        val incoming = timers.mapTo(HashSet<String>()) { it.id }
        val lost = store.timers().count { it.id !in incoming }
        if (lost == 0) {
            replace(timers)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.replace_title)
            .setMessage(resources.getQuantityString(R.plurals.replace_body, lost, lost))
            .setPositiveButton(R.string.import_replace) { _, _ -> replace(timers) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun replace(timers: List<Timer>) {
        store.replaceAll(timers)
        applied()
        say(resources.getQuantityString(R.plurals.imported_replaced, timers.size, timers.size))
    }

    /**
     * Redraws every widget, so one bound to a restored timer starts showing it
     * again without the user having to reconfigure it, and re-arms the refresh
     * alarm around whatever the new set of timers needs.
     */
    private fun applied() = WidgetUpdater.updateAll(this)

    // ------------------------------------------------------------------ helpers

    private fun say(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    /** Runs [block] on the main thread, unless the screen has gone away meanwhile. */
    private fun onUi(block: () -> Unit) = runOnUiThread {
        if (!isFinishing && !isDestroyed) block()
    }

    companion object {
        private const val MAX_IMPORT_BYTES = 1024 * 1024

        fun intent(context: Context): Intent = Intent(context, BackupActivity::class.java)
    }
}
