package com.afsh.messages.receivers

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences

/** In-memory preferences, so [com.afsh.messages.helpers.Config] runs on the JVM. */
internal class MemoryPreferences : SharedPreferences {
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

/** A context whose only working part is [MemoryPreferences]; every other call returns a stub default. */
internal class PreferencesContext : ContextWrapper(null) {
    val preferences = MemoryPreferences()
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = preferences
}
