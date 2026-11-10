package org.fossify.commons.extensions

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

fun <T> SharedPreferences.sharedPreferencesCallback(
    sendOnCollect: Boolean = false,
    value: () -> T?,
): Flow<T?> = callbackFlow {
    val sharedPreferencesListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(value())
        }

    if (sendOnCollect) {
        trySend(value())
    }
    val prefs = this@sharedPreferencesCallback
    prefs.registerOnSharedPreferenceChangeListener(sharedPreferencesListener)
    awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(sharedPreferencesListener) }
}
