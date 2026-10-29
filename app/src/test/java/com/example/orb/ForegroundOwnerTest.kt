package com.example.orb

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class ForegroundOwnerTest {
    @Test fun `rotation gap retains owner, final stop ends it, next start restores it`() {
        val app = Application()
        val events = mutableListOf<Boolean>()
        val owner = ForegroundOwner(app) { events.add(it) }
        val a = Activity(); val b = Activity()
        owner.onActivityStarted(a)
        owner.onActivityStopped(a)
        owner.onActivityStarted(b)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
        assertEquals(listOf(true), events)
        owner.onActivityStopped(b)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
        assertEquals(listOf(true, false), events)
        owner.onActivityStarted(a)
        assertEquals(listOf(true, false, true), events)
        owner.close()
    }

    @Test fun `system pairing dialog gap does not immediately tear down link`() {
        val app = Application()
        val events = mutableListOf<Boolean>()
        val owner = ForegroundOwner(app) { events.add(it) }
        val a = Activity()
        owner.onActivityStarted(a)
        owner.onActivityStopped(a)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)
        owner.onActivityStarted(a)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
        assertEquals(listOf(true), events)
        owner.close()
    }
}
