package org.fossify.messages.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import org.fossify.messages.ui.schedule.ScheduleTarget
import org.fossify.messages.ui.schedule.SendLaterScreen
import org.fossify.messages.ui.schedule.defaultSendTime

/** 12 · Schedule / send later. Returns the chosen send time; the caller schedules. */
class SendLaterActivity : ComposePageActivity() {
    private val target by lazy {
        ScheduleTarget(intent.getStringExtra(EXTRA_TEXT).orEmpty(), intent.getStringExtra(EXTRA_RECIPIENT).orEmpty())
    }
    private val initial by lazy { intent.getLongExtra(EXTRA_TIME, 0L).takeIf { it > 0 } ?: defaultSendTime() }

    @Composable
    override fun Page() = SendLaterScreen(target, initial, onBack = ::finish, onSchedule = ::choose)

    private fun choose(millis: Long) {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_TIME, millis))
        finish()
    }

    /** Input: what is being scheduled and when it goes now, if it already has a time. Output: the new time, or null. */
    data class Request(val target: ScheduleTarget, val time: Long? = null)

    class Contract : ActivityResultContract<Request, Long?>() {
        override fun createIntent(context: Context, input: Request): Intent =
            Intent(context, SendLaterActivity::class.java)
                .putExtra(EXTRA_TEXT, input.target.text)
                .putExtra(EXTRA_RECIPIENT, input.target.recipient)
                .putExtra(EXTRA_TIME, input.time ?: 0L)

        override fun parseResult(resultCode: Int, intent: Intent?): Long? =
            intent?.getLongExtra(EXTRA_TIME, 0L)?.takeIf { resultCode == Activity.RESULT_OK && it > 0 }
    }

    private companion object {
        const val EXTRA_TEXT = "text"
        const val EXTRA_RECIPIENT = "recipient"
        const val EXTRA_TIME = "time"
    }
}
