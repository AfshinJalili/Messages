package org.fossify.messages

import android.graphics.Bitmap
import android.view.Choreographer
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Whole-screen captures in app-private storage. tools/verify.sh pulls them with run-as into .scratch/qa/<branch>/screenshots/. */
object Screenshots {
    fun capture(name: String): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        awaitFrames(instrumentation)
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

    /**
     * Idle means the change is composed, not that it is on screen: the screenshot reads what the
     * display shows, which can still be the previous state. A few frames later it is not.
     */
    private fun awaitFrames(instrumentation: android.app.Instrumentation) {
        val drawn = CountDownLatch(1)
        instrumentation.runOnMainSync {
            fun next(left: Int) {
                Choreographer.getInstance().postFrameCallback { if (left <= 1) drawn.countDown() else next(left - 1) }
            }
            next(FRAMES)
        }
        drawn.await(FRAME_TIMEOUT_S, TimeUnit.SECONDS)
    }

    private const val FRAMES = 3
    private const val FRAME_TIMEOUT_S = 2L
}
