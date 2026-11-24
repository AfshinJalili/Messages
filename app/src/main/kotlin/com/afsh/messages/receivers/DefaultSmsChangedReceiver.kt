package com.afsh.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony.Sms.Intents.ACTION_DEFAULT_SMS_PACKAGE_CHANGED
import android.provider.Telephony.Sms.Intents.EXTRA_IS_DEFAULT_SMS_APP
import com.afsh.messages.helpers.InboxRepository

/**
 * Android tells the old and the new default SMS app when the default changes. While another app
 * was the default, nothing here saw its messages, so on becoming the default again this asks for a
 * forced reconcile. It only records the request: the next launch or resume runs it, so a role
 * switch from system Settings does not wake a full Telephony pass in the background.
 */
class DefaultSmsChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DEFAULT_SMS_PACKAGE_CHANGED) return
        if (intent.getBooleanExtra(EXTRA_IS_DEFAULT_SMS_APP, false)) {
            InboxRepository.requestForceReconcile(context)
        }
    }
}
