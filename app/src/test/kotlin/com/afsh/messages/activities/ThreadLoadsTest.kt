package com.afsh.messages.activities

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class ThreadLoadsTest {

    /** Runs what it is given only when told to, in order. */
    private class ManualExecutor : Executor {
        val queued = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) {
            queued.addLast(command)
        }
        fun runNext() = queued.removeFirst().run()
    }

    @Test
    fun `an older provider read never lands after a newer one`() {
        val loads = ThreadLoads.serial()
        var shown = ""
        val newerShown = CountDownLatch(1)
        val done = CountDownLatch(2)

        loads.load {
            val read = "older"
            // Were loads to overlap, the newer one would finish here, before this one shows its read.
            newerShown.await(300, TimeUnit.MILLISECONDS)
            shown = read
            done.countDown()
        }
        loads.reload {
            shown = "newer"
            newerShown.countDown()
            done.countDown()
        }

        assertEquals(true, done.await(5, TimeUnit.SECONDS))
        assertEquals("newer", shown)
    }

    @Test
    fun `reloads asked for while one waits to start run once`() {
        val executor = ManualExecutor()
        val loads = ThreadLoads(executor)
        var reloads = 0

        repeat(3) { loads.reload { reloads++ } }

        assertEquals(1, executor.queued.size)
        executor.runNext()
        assertEquals(1, reloads)
    }

    @Test
    fun `a reload asked for while one runs runs again after it, to read what changed meanwhile`() {
        val executor = ManualExecutor()
        val loads = ThreadLoads(executor)
        var reloads = 0
        loads.reload {
            reloads++
            loads.reload { reloads++ }
        }

        executor.runNext()
        assertEquals(1, executor.queued.size)
        executor.runNext()

        assertEquals(2, reloads)
    }

    @Test
    fun `a load is never dropped`() {
        val executor = ManualExecutor()
        val loads = ThreadLoads(executor)

        loads.reload {}
        loads.load {}
        loads.load {}

        assertEquals(3, executor.queued.size)
    }
}
