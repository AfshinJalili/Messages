package org.fossify.messages

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Whole-screen captures in app-private storage. tools/verify.sh pulls them with run-as into .scratch/qa/<branch>/screenshots/. */
object Screenshots {
    fun capture(name: String): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Screenshot $name failed" }
        val dir = File(instrumentation.targetContext.filesDir, "screenshots")
        val file = File(dir.apply { mkdirs() }, "$name.png")
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
        return file
    }
}
