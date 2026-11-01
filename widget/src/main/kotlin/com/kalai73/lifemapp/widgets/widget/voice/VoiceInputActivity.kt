package com.kalai73.lifemapp.widgets.widget.voice

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.kalai73.lifemapp.widgets.core.data.LifeMappGraph
import com.kalai73.lifemapp.widgets.core.data.api.VoiceIntent
import com.kalai73.lifemapp.widgets.core.data.api.VoiceItem
import com.kalai73.lifemapp.widgets.core.data.auth.NotSignedInException
import com.kalai73.lifemapp.widgets.core.data.repository.VoiceRepository.Understood
import com.kalai73.lifemapp.widgets.core.weblauncher.WebLauncher
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes
import com.kalai73.lifemapp.widgets.core.data.repository.SyncResult
import com.kalai73.lifemapp.widgets.widget.LifeMappWidgets
import com.kalai73.lifemapp.widgets.widget.R
import com.kalai73.lifemapp.widgets.widget.sync.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

/**
 * The widget's mic button, without the browser. Opens the system speech dialog, then does what the web's mic
 * does after transcribing (see VoiceRepository): /api/parse-voice-items splits what was said into tasks and
 * reminders, charging the voice input's AI credits, and /api/widget/voice-items files them in the user's task
 * manager (LIFE MAPP's TODAY list, Todoist or Google Tasks) and the Integrity domain's Reminders, encrypted
 * where the account is. A single item is shown for review first, as on the web; several are added at once.
 * The widgets refresh afterwards.
 *
 * The system recognizer listens in the LIFE MAPP language when that differs from the phone's, and needs no
 * microphone permission from this app. Only a device without any speech recognizer falls back to the web's
 * own mic, which records and transcribes on the server.
 *
 * Started with [EXTRA_REMINDERS] (the Integrity card's Reception), everything said becomes reminders.
 */
class VoiceInputActivity : Activity() {
    private val asReminders: Boolean get() = intent.getBooleanExtra(EXTRA_REMINDERS, false)
    private val typed: Boolean get() = intent.getBooleanExtra(EXTRA_TYPED, false)
    private val scope =CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val graph by lazy { LifeMappGraph.get(applicationContext) }
    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recreated after the process died while the speech dialog was open: the result still comes back here
        if (savedInstanceState != null) return
        if (typed) {
            // Typing: straight to the editable dialog, no speech and no AI credits
            review(VoiceItem("", if (asReminders) VoiceIntent.REMINDER else VoiceIntent.TASK))
            return
        }
        scope.launch {
            val language = withContext(Dispatchers.IO) {
                runCatching { graph.cache.current().snapshot?.settings?.language }.getOrNull()
            }
            listen(recognizerLanguage(language, Locale.getDefault()))
        }
    }

    override fun onDestroy() {
        dialog?.dismiss()
        scope.cancel()
        super.onDestroy()
    }

    private fun listen(language: String?) {
        val listen = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(if (asReminders) R.string.voice_prompt_reminder else R.string.voice_prompt))
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .apply { if (language != null) putExtra(RecognizerIntent.EXTRA_LANGUAGE, language) }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(listen, REQUEST_SPEECH)
        } catch (_: ActivityNotFoundException) {
            // No speech recognizer on this device: the web's mic records and transcribes instead
            runCatching { startActivity(WebLauncher.routeIntent(this, WebRoutes.TODAY, WebRoutes.VOICE_RECORD_FRAGMENT)) }
            finishQuietly()
        }
    }

    @Deprecated("Plain Activity result API: this trampoline has no AndroidX activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SPEECH) return
        val transcript = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (resultCode == RESULT_OK && !transcript.isNullOrEmpty()) {
            understand(transcript)
        } else {
            // Cancelled or nothing heard
            finishQuietly()
        }
    }

    private fun understand(transcript: String) {
        showProgress(R.string.voice_analyzing)
        scope.launch {
            val understood = attempt { graph.voice.understand(transcript, asReminders) } ?: return@launch
            Log.i(TAG, "understood: " + when (understood) {
                is Understood.Items -> understood.items.joinToString { it.intent.name }
                else -> understood.toString()
            })
            when (understood) {
                Understood.NoCredits -> finishWith(getString(R.string.voice_no_credits))
                Understood.Nothing -> finishQuietly()
                is Understood.Items ->
                    if (understood.items.size == 1) review(understood.items.single()) else file(understood.items)
            }
        }
    }

    /** One item: editable before it's added, and switchable between task and reminder (outside the Reception). */
    private fun review(item: VoiceItem) {
        dialog?.dismiss()
        var intent = item.intent
        val input = EditText(dialogContext()).apply {
            setText(item.text)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine()
        }
        val container = FrameLayout(dialogContext()).apply {
            val side = dp(24)
            setPadding(side, dp(8), side, 0)
            addView(input)
        }
        val add = { file(listOf(VoiceItem(input.text.toString().trim(), intent))) }
        val review = AlertDialog.Builder(dialogContext())
            .setTitle(titleFor(intent))
            .setView(container)
            .setPositiveButton(R.string.voice_add, null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> finishQuietly() }
            .apply { if (!asReminders) setNeutralButton(switchLabelFor(intent), null) }
            .setOnCancelListener { finishQuietly() }
            .create()
        review.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        review.setOnShowListener {
            val positive = review.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener { if (input.text.isNotBlank()) add() }
            // Switching keeps the dialog open
            review.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { button ->
                intent = if (intent == VoiceIntent.TASK) VoiceIntent.REMINDER else VoiceIntent.TASK
                review.setTitle(titleFor(intent))
                (button as TextView).setText(switchLabelFor(intent))
            }
        }
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE && input.text.isNotBlank()) add()
            true
        }
        dialog = review
        review.show()
    }

    private fun file(items: List<VoiceItem>) {
        showProgress(R.string.voice_adding)
        scope.launch {
            val results = attempt { graph.voice.file(items) } ?: return@launch
            val outcome = VoiceOutcome.of(results)
            Log.i(TAG, "filed: $outcome, encrypted=${graph.encryption.dataKey() != null}")
            if (outcome.added > 0) reloadWidgets()
            finishWith(outcome.message(this@VoiceInputActivity))
        }
    }

    /**
     * Shows the new tasks and reminders at once: the snapshot is reloaded right here (the "Adding…" dialog stays
     * up meanwhile) and every widget, and the app's Today screen, redraws from it. A queued sync, which the system
     * may hold back for a while, only covers a failed or interrupted reload.
     */
    private suspend fun reloadWidgets() {
        var reloaded = false
        try {
            graph.snapshots.markSyncing()
            LifeMappWidgets.updateAll(applicationContext) // spinner
            reloaded = graph.snapshots.refresh() == SyncResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "reload failed: ${e::class.simpleName}")
        } finally {
            withContext(NonCancellable) {
                graph.snapshots.clearSyncing()
                LifeMappWidgets.updateAll(applicationContext)
                if (!reloaded) SyncScheduler.syncNow(applicationContext)
            }
        }
    }

    /** Runs [block]; on failure tells the user why nothing was added and finishes, returning null. */
    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: NotSignedInException) {
        Log.w(TAG, "signed out")
        finishWith(getString(R.string.voice_signed_out)); null
    } catch (e: IOException) {
        // Offline, or a server error (ServerException is an IOException)
        Log.w(TAG, "request failed: $e")
        finishWith(getString(R.string.voice_offline)); null
    } catch (e: Exception) {
        Log.e(TAG, "unexpected", e)
        finishWith("${getString(R.string.voice_nothing_added)} ${getString(R.string.voice_reason_failed)}"); null
    }

    private fun showProgress(message: Int) {
        dialog?.dismiss()
        val context = dialogContext()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            addView(ProgressBar(context).apply { isIndeterminate = true }, LinearLayout.LayoutParams(dp(32), dp(32)))
            addView(TextView(context).apply {
                setText(message)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(dp(16), 0, 0, 0)
            })
        }
        dialog = AlertDialog.Builder(context)
            .setView(row)
            // Backing out doesn't undo a request already sent, but stops waiting for it
            .setOnCancelListener { finishQuietly() }
            .create()
            .also { it.show() }
    }

    private fun titleFor(intent: VoiceIntent) = if (intent == VoiceIntent.TASK) R.string.voice_new_task else R.string.voice_new_reminder
    private fun switchLabelFor(intent: VoiceIntent) = if (intent == VoiceIntent.TASK) R.string.voice_make_reminder else R.string.voice_make_task

    private fun dialogContext(): Context {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return android.view.ContextThemeWrapper(
            this,
            if (night) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert,
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun finishWith(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        finishQuietly()
    }

    private fun finishQuietly() {
        if (isFinishing) return
        dialog?.dismiss()
        dialog = null
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val REQUEST_SPEECH = 1
        /** Logs outcomes only (intents, counts, reasons), never what was said */
        private const val TAG = "LifeMappVoice"

        /**
         * The language to listen in: null (the phone's own, with its region) when LIFE MAPP is in the phone's
         * language, else the LIFE MAPP language ("es" for a Spanish account on an English phone).
         */
        internal fun recognizerLanguage(appLanguage: String?, device: Locale): String? {
            val app = appLanguage?.substringBefore('-')?.substringBefore('_')?.trim()?.lowercase()
            if (app.isNullOrEmpty() || app == device.language) return null
            return app
        }
        const val EXTRA_REMINDERS = "com.kalai73.lifemapp.widgets.extra.VOICE_REMINDERS"

        const val EXTRA_TYPED = "com.kalai73.lifemapp.widgets.extra.VOICE_TYPED"

        /**
         * [reminders]: file everything said as reminders (the Reception) instead of letting the parser sort it.
         * [typed]: skip the microphone and open the editable dialog for a new task (or reminder) to type.
         */
        fun intent(context: Context, reminders: Boolean = false, typed: Boolean = false): Intent =
            Intent(context, VoiceInputActivity::class.java)
                .putExtra(EXTRA_REMINDERS, reminders)
                .putExtra(EXTRA_TYPED, typed)
                // Distinct data so the buttons' PendingIntents don't collapse into one (extras don't count)
                .setData(Uri.parse("lifemapp-voice:" + (if (reminders) "reminders" else "any") + (if (typed) "-typed" else "")))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
