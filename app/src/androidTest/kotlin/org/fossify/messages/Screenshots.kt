package org.fossify.messages

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Whole-screen captures for QA evidence. tools/verify.sh pulls them into .scratch/qa/<branch>/screenshots/. */
object Screenshots {
    fun capture(name: String): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Screenshot $name failed" }
        val dir = checkNotNull(instrumentation.targetContext.getExternalFilesDir("screenshots")) { "No screenshot directory" }
        val file = File(dir.apply { mkdirs() }, "$name.png")
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
        return file
    }
}
