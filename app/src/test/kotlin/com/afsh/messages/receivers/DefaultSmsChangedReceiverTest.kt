package com.afsh.messages.receivers

import android.content.Intent
import android.provider.Telephony.Sms.Intents.ACTION_DEFAULT_SMS_PACKAGE_CHANGED
import android.provider.Telephony.Sms.Intents.EXTRA_IS_DEFAULT_SMS_APP
import com.afsh.messages.extensions.config
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** android.jar stubs return null/false here, so the broadcast's action and extra are supplied directly. */
private fun defaultSmsChanged(isDefault: Boolean, action: String = ACTION_DEFAULT_SMS_PACKAGE_CHANGED) =
    object : Intent() {
        override fun getAction(): String = action
        override fun getBooleanExtra(name: String?, defaultValue: Boolean): Boolean =
            if (name == EXTRA_IS_DEFAULT_SMS_APP) isDefault else defaultValue
    }

class DefaultSmsChangedReceiverTest {

    @Test
    fun `becoming the default SMS app records a forced reconcile`() {
        val context = PreferencesContext()
        DefaultSmsChangedReceiver().onReceive(context, defaultSmsChanged(isDefault = true))
        assertTrue(context.config.forceReconcilePending)
    }

    @Test
    fun `becoming the default SMS app reopens the message import`() {
        val context = PreferencesContext()
        context.config.messagesImported = true
        context.config.importHandledThreads = setOf("4")
        context.config.importThreadFailures = setOf("5:3")
        DefaultSmsChangedReceiver().onReceive(context, defaultSmsChanged(isDefault = true))
        assertFalse(context.config.messagesImported)
        assertTrue(context.config.importHandledThreads.isEmpty())
        assertTrue(context.config.importThreadFailures.isEmpty())
    }

    @Test
    fun `losing the default SMS app records nothing`() {
        val context = PreferencesContext()
        context.config.messagesImported = true
        DefaultSmsChangedReceiver().onReceive(context, defaultSmsChanged(isDefault = false))
        assertFalse(context.config.forceReconcilePending)
        assertTrue(context.config.messagesImported)
    }

    @Test
    fun `another action records nothing`() {
        val context = PreferencesContext()
        DefaultSmsChangedReceiver().onReceive(context, defaultSmsChanged(isDefault = true, action = "other"))
        assertFalse(context.config.forceReconcilePending)
    }

    @Test
    fun `receiver only records the request and the reconcile consumes it`() {
        val receiver = File("src/main/kotlin/com/afsh/messages/receivers/DefaultSmsChangedReceiver.kt").readText()
        assertFalse(receiver.contains("scheduleProviderReconcile"))
        assertFalse(receiver.contains("refreshInbox"))
        val repository = File("src/main/kotlin/com/afsh/messages/helpers/InboxRepository.kt").readText()
        // A pending request picks the full pass (see InboxRepositoryTest), and is cleared only after success.
        assertTrue(Regex("""providerReconcilePass\(force, requested\)""").containsMatchIn(repository))
        assertTrue(Regex("""onFinished = \{\s*handler\.post \{\s*if \(requested\) context\.config\.forceReconcilePending = false""").containsMatchIn(repository))
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.provider.action.DEFAULT_SMS_PACKAGE_CHANGED"))
    }
}
