package com.afsh.messages

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.afsh.messages.extensions.config
import com.afsh.messages.helpers.Config

/**
 * Settings in a throwaway preferences file, so a check never reads or writes the app's real ones.
 * [context] is also its own application context, because `context.config` reads through the
 * application context and would otherwise reach the real settings.
 */
class IsolatedPreferences(private val file: String) {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext

    val context: Context = object : ContextWrapper(target) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int) = target.getSharedPreferences(file, mode)
    }

    val config: Config get() = context.config

    /** Empties and deletes the file. Config writes with apply(); commit first so a pending write cannot recreate it. */
    fun clear() {
        target.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear().commit()
        target.deleteSharedPreferences(file)
    }

    /** Runs [block] on an empty file and clears it afterwards. */
    fun <T> use(block: (IsolatedPreferences) -> T): T {
        clear()
        return try {
            block(this)
        } finally {
            clear()
        }
    }
}
