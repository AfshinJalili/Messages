package com.afsh.messages.receivers

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Telephony.Sms.Intents.ACTION_DEFAULT_SMS_PACKAGE_CHANGED
import android.provider.Telephony.Sms.Intents.EXTRA_IS_DEFAULT_SMS_APP
import com.afsh.messages.extensions.config
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** In-memory preferences, so [com.afsh.messages.helpers.Config] runs on the JVM. */
private class MemoryPreferences : SharedPreferences {
    val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?) =
        values[key] as MutableSet<String>? ?: defValues
    override fun getInt(key: String, defValue: Int) = values[key] as Int? ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
    override fun contains(key: String) = key in values
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { values[key] = value }
        override fun putStringSet(key: String, value: MutableSet<String>?) = apply { values[key] = value }
        override fun putInt(key: String, value: Int) = apply { values[key] = value }
        override fun putLong(key: String, value: Long) = apply { values[key] = value }
        override fun putFloat(key: String, value: Float) = apply { values[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }
        override fun remove(key: String) = apply { values.remove(key) }
        override fun clear() = apply { values.clear() }
        override fun commit() = true
        override fun apply() = Unit
    }
}

private class PreferencesContext : ContextWrapper(null) {
    val preferences = MemoryPreferences()
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = preferences
}

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
    fun `losing the default SMS app records nothing`() {
        val context = PreferencesContext()
        DefaultSmsChangedReceiver().onReceive(context, defaultSmsChanged(isDefault = false))
        assertFalse(context.config.forceReconcilePending)
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
        // The dirty-check is skipped while a request is pending, and cleared only after success.
        assertTrue(Regex("""needsFull = force \|\| requested \|\| needsFullReconcile""").containsMatchIn(repository))
        assertTrue(Regex("""onFinished = \{\s*if \(requested\) context\.config\.forceReconcilePending = false""").containsMatchIn(repository))
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.provider.action.DEFAULT_SMS_PACKAGE_CHANGED"))
    }
}
