package com.afsh.messages.receivers

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsSentReceiverTest {
    private val appDir = Files.createTempDirectory("app").toFile()
    private val cacheDir = File(appDir, "cache").apply { mkdirs() }

    @Test
    fun `the PDU file android-smsmms wrote to the cache may be deleted`() {
        assertTrue(MmsSentReceiver.isSentPduFile(File(cacheDir, "send.123.dat").path, cacheDir))
    }

    @Test
    fun `a path outside the cache is never deleted`() {
        assertFalse(MmsSentReceiver.isSentPduFile(File(appDir, "databases/messages.db").path, cacheDir))
        assertFalse(MmsSentReceiver.isSentPduFile(cacheDir.path, cacheDir))
        assertFalse(MmsSentReceiver.isSentPduFile(File(cacheDir, "nested/send.1.dat").path, cacheDir))
    }

    @Test
    fun `a path that climbs out of the cache is never deleted`() {
        assertFalse(MmsSentReceiver.isSentPduFile("${cacheDir.path}/../databases/messages.db", cacheDir))
    }
}
