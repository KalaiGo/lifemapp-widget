package com.kalai73.lifemapp.widgets.widget.voice

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import com.kalai73.lifemapp.widgets.core.data.LifeMappGraph
import com.kalai73.lifemapp.widgets.core.weblauncher.WebLauncher
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes
import com.kalai73.lifemapp.widgets.widget.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The widget's mic button. Opens the system speech dialog right away, without waiting for a browser, and hands
 * what was said to the web (/today#voice=…). The web then does what its own mic does after transcribing:
 * /api/parse-voice-items splits the text into tasks and reminders and they are created in the user's task
 * manager (local, Todoist or Google) and reminders list, encrypted where needed. A single item opens the
 * review dialog first.
 *
 * Speech is recognized on the device; the web charges the voice input's AI credits when it parses the
 * transcript, as its own mic does when it transcribes. The system recognizer listens in the LIFE MAPP language
 * when that differs from the phone's, and needs no microphone permission from this app.
 *
 * Started with [EXTRA_REMINDERS] (the Integrity card's Reception), everything said becomes reminders.
 */
class VoiceInputActivity : Activity() {
    private val asReminders: Boolean get() = intent.getBooleanExtra(EXTRA_REMINDERS, false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recreated (e.g. rotated) while the dialog is open: the result still comes back here
        if (savedInstanceState != null) return
        scope.launch {
            val language = withContext(Dispatchers.IO) {
                runCatching { LifeMappGraph.get(applicationContext).cache.current().snapshot?.settings?.language }.getOrNull()
            }
            listen(recognizerLanguage(language, Locale.getDefault()))
        }
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
            open(WebLauncher.routeIntent(this, WebRoutes.TODAY, WebRoutes.VOICE_RECORD_FRAGMENT))
        }
    }

    @Deprecated("Plain Activity result API: this trampoline has no AndroidX activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SPEECH) return
        val transcript = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (resultCode == RESULT_OK && !transcript.isNullOrEmpty()) {
            open(WebLauncher.routeIntent(this, WebRoutes.TODAY, WebRoutes.voiceFragment(transcript, asReminders)))
        } else {
            // Cancelled or nothing heard
            finishQuietly()
        }
    }

    private fun open(intent: Intent) {
        runCatching { startActivity(intent) }
        finishQuietly()
    }

    private fun finishQuietly() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val REQUEST_SPEECH = 1

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

        /** [reminders]: file everything said as reminders (the Reception) instead of letting the web sort it. */
        fun intent(context: Context, reminders: Boolean = false): Intent =
            Intent(context, VoiceInputActivity::class.java)
                .putExtra(EXTRA_REMINDERS, reminders)
                // Distinct data so the two buttons' PendingIntents don't collapse into one (extras don't count)
                .setData(Uri.parse(if (reminders) "lifemapp-voice:reminders" else "lifemapp-voice:any"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
