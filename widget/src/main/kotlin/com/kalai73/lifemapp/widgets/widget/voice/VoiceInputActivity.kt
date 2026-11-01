package com.kalai73.lifemapp.widgets.widget.voice

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import com.kalai73.lifemapp.widgets.core.weblauncher.WebLauncher
import com.kalai73.lifemapp.widgets.core.weblauncher.WebRoutes
import com.kalai73.lifemapp.widgets.widget.R

/**
 * The widget's mic button. Opens the system speech dialog right away, without waiting for a browser, and hands
 * what was said to the web (/today#voice=…). The web then does what its own mic does after transcribing:
 * /api/parse-voice-items splits the text into tasks and reminders and they are created in the user's task
 * manager (local, Todoist or Google) and reminders list, encrypted where needed. A single item opens the
 * review dialog first.
 *
 * Speech is recognized on the device, so, like the web's browser speech recognition, it uses no AI credits.
 * The system recognizer needs no microphone permission from this app.
 */
class VoiceInputActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recreated (e.g. rotated) while the dialog is open: the result still comes back here
        if (savedInstanceState != null) return
        val listen = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt))
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
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
            open(WebLauncher.routeIntent(this, WebRoutes.TODAY, WebRoutes.voiceFragment(transcript)))
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

        fun intent(context: Context): Intent =
            Intent(context, VoiceInputActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
