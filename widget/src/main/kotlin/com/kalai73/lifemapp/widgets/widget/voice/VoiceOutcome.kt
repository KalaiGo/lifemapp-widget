package com.kalai73.lifemapp.widgets.widget.voice

import android.content.Context
import com.kalai73.lifemapp.widgets.core.data.api.VoiceFailure
import com.kalai73.lifemapp.widgets.core.data.api.VoiceIntent
import com.kalai73.lifemapp.widgets.core.data.api.VoiceItemResult
import com.kalai73.lifemapp.widgets.widget.R

/**
 * What voice input added, for the message shown afterwards.
 * [kind]: what was added when everything added is of one kind; [reason]: the failure most worth telling.
 */
internal data class VoiceOutcome(val added: Int, val notAdded: Int, val kind: VoiceIntent?, val reason: VoiceFailure?) {
    fun message(context: Context): String {
        val reasonText = reason?.let { context.getString(reasonText(it)) }
        val main = when {
            notAdded == 0 && added == 1 ->
                context.getString(if (kind == VoiceIntent.REMINDER) R.string.voice_reminder_added else R.string.voice_task_added)
            notAdded == 0 -> context.resources.getQuantityString(R.plurals.voice_items_added, added, added)
            added == 0 -> context.getString(R.string.voice_nothing_added)
            else -> context.getString(R.string.voice_partly_added, added, notAdded)
        }
        return if (reasonText != null) "$main $reasonText" else main
    }

    companion object {
        /** Something the user can fix comes first */
        private val PRIORITY = listOf(VoiceFailure.LOCKED, VoiceFailure.NOT_CONNECTED, VoiceFailure.UNSUPPORTED, VoiceFailure.LIMIT, VoiceFailure.FAILED)

        fun of(results: List<VoiceItemResult>): VoiceOutcome {
            val ok = results.filter { it.ok }
            val failed = results.filterNot { it.ok }
            return VoiceOutcome(
                added = ok.size,
                notAdded = failed.size,
                kind = ok.map { it.kind }.distinct().singleOrNull(),
                // A server answering with no results added nothing either
                reason = failed.map { it.reason ?: VoiceFailure.FAILED }.minByOrNull { PRIORITY.indexOf(it) }
                    ?: if (results.isEmpty()) VoiceFailure.FAILED else null,
            )
        }

        fun reasonText(reason: VoiceFailure): Int = when (reason) {
            VoiceFailure.LOCKED -> R.string.voice_reason_locked
            VoiceFailure.LIMIT -> R.string.voice_reason_limit
            VoiceFailure.NOT_CONNECTED -> R.string.voice_reason_not_connected
            VoiceFailure.UNSUPPORTED -> R.string.voice_reason_unsupported
            VoiceFailure.FAILED -> R.string.voice_reason_failed
        }
    }
}
