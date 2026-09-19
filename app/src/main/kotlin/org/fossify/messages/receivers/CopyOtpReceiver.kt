package org.fossify.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.notificationManager
import org.fossify.messages.helpers.OTP_CODE
import org.fossify.messages.helpers.THREAD_ID

class CopyOtpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = intent.getStringExtra(OTP_CODE) ?: return
        context.copyToClipboard(code)
        context.notificationManager.cancel(intent.getLongExtra(THREAD_ID, 0L).hashCode())
    }
}
