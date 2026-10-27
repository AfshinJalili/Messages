package org.fossify.messages.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import org.fossify.messages.R
import org.fossify.messages.ui.components.confirm
import org.fossify.messages.ui.schedule.ScheduleTarget
import org.fossify.messages.ui.schedule.ScheduledEvent
import org.fossify.messages.ui.schedule.ScheduledMessageScreen

/**
 * 47 · Scheduled / edit. It only decides; the thread that opened it saves, sends or cancels, so
 * sending and scheduling keep one implementation.
 */
class ScheduledMessageActivity : ComposePageActivity() {
    private val target by lazy {
        ScheduleTarget(intent.getStringExtra(EXTRA_TEXT).orEmpty(), intent.getStringExtra(EXTRA_RECIPIENT).orEmpty())
    }
    private val originalTime by lazy { intent.getLongExtra(EXTRA_TIME, 0L) }
    private var time by mutableLongStateOf(0L)

    private val sendLater = registerForActivityResult(SendLaterActivity.Contract()) { chosen ->
        if (chosen != null) time = chosen
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        time = savedInstanceState?.getLong(EXTRA_TIME) ?: originalTime
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(EXTRA_TIME, time)
    }

    @Composable
    override fun Page() = ScheduledMessageScreen(target, time, timeChanged = time != originalTime, onEvent = ::onEvent)

    private fun onEvent(event: ScheduledEvent) {
        when (event) {
            ScheduledEvent.Back -> finish()
            // Only the time: the text being edited stays on this page.
            ScheduledEvent.ChangeTime -> sendLater.launch(SendLaterActivity.Request(ScheduleTarget("", target.recipient), time))
            is ScheduledEvent.Save -> finishWith(Decision.Save(event.text, time))
            ScheduledEvent.SendNow -> finishWith(Decision.SendNow)
            ScheduledEvent.Cancel -> confirm(
                title = getString(R.string.cancel_scheduled_message_title),
                explanation = getString(R.string.cancel_scheduled_message_text),
                confirmLabel = getString(R.string.cancel_scheduled_message),
                destructive = true,
            ) { finishWith(Decision.Cancel) }
        }
    }

    private fun finishWith(decision: Decision) {
        val data = Intent().putExtra(EXTRA_MESSAGE_ID, intent.getLongExtra(EXTRA_MESSAGE_ID, 0L))
        when (decision) {
            is Decision.Save -> data.putExtra(EXTRA_DECISION, SAVE).putExtra(EXTRA_TEXT, decision.text).putExtra(EXTRA_TIME, decision.time)
            Decision.SendNow -> data.putExtra(EXTRA_DECISION, SEND_NOW)
            Decision.Cancel -> data.putExtra(EXTRA_DECISION, CANCEL)
        }
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    /** What the person decided for the scheduled message. */
    sealed interface Decision {
        data class Save(val text: String, val time: Long) : Decision
        data object SendNow : Decision
        data object Cancel : Decision
    }

    data class Request(val messageId: Long, val target: ScheduleTarget, val time: Long)

    /** Output: the message id and the decision, or null when the page was left without one. */
    class Contract : ActivityResultContract<Request, Pair<Long, Decision>?>() {
        override fun createIntent(context: Context, input: Request): Intent =
            Intent(context, ScheduledMessageActivity::class.java)
                .putExtra(EXTRA_MESSAGE_ID, input.messageId)
                .putExtra(EXTRA_TEXT, input.target.text)
                .putExtra(EXTRA_RECIPIENT, input.target.recipient)
                .putExtra(EXTRA_TIME, input.time)

        override fun parseResult(resultCode: Int, intent: Intent?): Pair<Long, Decision>? {
            if (resultCode != Activity.RESULT_OK || intent == null) return null
            val decision = when (intent.getIntExtra(EXTRA_DECISION, -1)) {
                SAVE -> Decision.Save(intent.getStringExtra(EXTRA_TEXT).orEmpty(), intent.getLongExtra(EXTRA_TIME, 0L))
                SEND_NOW -> Decision.SendNow
                CANCEL -> Decision.Cancel
                else -> return null
            }
            return intent.getLongExtra(EXTRA_MESSAGE_ID, 0L) to decision
        }
    }

    private companion object {
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_TEXT = "text"
        const val EXTRA_RECIPIENT = "recipient"
        const val EXTRA_TIME = "time"
        const val EXTRA_DECISION = "decision"
        const val SAVE = 0
        const val SEND_NOW = 1
        const val CANCEL = 2
    }
}
