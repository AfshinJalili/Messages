package com.afsh.messages.services

import android.app.Service
import android.content.Intent
import com.klinker.android.send_message.Settings
import com.afsh.messages.helpers.SmsIntentParser
import com.afsh.messages.messaging.sendMessageCompat

class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (intent == null) {
                return START_NOT_STICKY
            }

            val addresses = SmsIntentParser.parseRecipientsFromUri(intent.data).orEmpty()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (addresses.isNotEmpty() && !text.isNullOrEmpty()) {
                val subId = Settings.DEFAULT_SUBSCRIPTION_ID
                sendMessageCompat(text, addresses, subId, emptyList())
            }
        } catch (ignored: Exception) {
        }

        return super.onStartCommand(intent, flags, startId)
    }
}
