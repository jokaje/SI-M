package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.CreatureDna
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SI-M", appName)
    }

    @Test
    fun `test creature random generation and hidden intelligence`() {
        val creature = CreatureDna.createRandom()
        assertNotNull(creature.name)
        assertTrue(creature.ears in 0..7)
        assertTrue(creature.crown in 0..4)
        assertNotNull(creature.profile.personalityProfile)
        assertNotNull(creature.profile.speechStyle)
    }
}
