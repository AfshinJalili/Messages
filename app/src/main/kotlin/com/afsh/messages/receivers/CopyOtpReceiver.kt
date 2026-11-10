package com.afsh.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.notificationManager
import com.afsh.messages.helpers.OTP_CODE
import com.afsh.messages.helpers.THREAD_ID

class CopyOtpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = intent.getStringExtra(OTP_CODE) ?: return
        context.copyToClipboard(code)
        context.notificationManager.cancel(intent.getLongExtra(THREAD_ID, 0L).hashCode())
    }
}
