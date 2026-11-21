package com.afsh.messages.testdata

import android.app.role.RoleManager
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Seeds ~2k threads / ~25k synthetic Persian SMS into the Telephony provider.
 * Requires the default SMS role (granted by tools/verify.sh / the macrobenchmark workflow).
 * Not part of the PR emulator gate — run explicitly or from macrobenchmark.yml.
 */
@RunWith(AndroidJUnit4::class)
class InboxSeederChecks {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun seedSyntheticInbox() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Pass -e seedInbox 1 to run the volume seeder (skipped in the PR emulator gate)",
            args.getString("seedInbox") == "1",
        )
        assertTrue(
            "Default SMS role required to write Telephony " +
                "(package=${context.packageName}, default=${defaultSmsPackage()}, role=${holdsSmsRole()})",
            isDefaultSmsApp(),
        )
        val before = countAllSms()
        val result = SyntheticInboxSeeder.seed(context)
        Log.i(
            TAG,
            "Seeded threads=${result.threads} messages=${result.messages} " +
                "unread=${result.unread} in ${result.elapsedMs}ms",
        )
        assertEquals(SyntheticInboxSeeder.TARGET_THREADS, result.threads)
        assertEquals(SyntheticInboxSeeder.TARGET_MESSAGES, result.messages)
        val after = countAllSms()
        assertEquals(
            "Provider row delta must match inserted count (before=$before after=$after)",
            result.messages.toLong(),
            after - before,
        )
        assertTrue("Seed should finish in under 15 minutes on CI AVD", result.elapsedMs < 15 * 60_000L)
    }

    private fun isDefaultSmsApp(): Boolean {
        if (defaultSmsPackage() == context.packageName) return true
        return holdsSmsRole()
    }

    private fun defaultSmsPackage(): String? = Telephony.Sms.getDefaultSmsPackage(context)

    private fun holdsSmsRole(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roles = context.getSystemService(RoleManager::class.java) ?: return false
        return roles.isRoleHeld(RoleManager.ROLE_SMS)
    }

    private fun countAllSms(): Long {
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            null,
            null,
            null,
        ).use { cursor ->
            return cursor?.count?.toLong() ?: 0L
        }
    }

    companion object {
        private const val TAG = "InboxSeeder"
    }
}
