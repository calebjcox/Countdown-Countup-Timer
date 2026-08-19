package com.calebjcox.countdownwidgets.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RowVisibilityTest {

    @Test
    fun `only when-room consults the cell`() {
        // The point of the other two: they are the answers a size cannot give, so a
        // change of cell must not move them.
        for (hasRoom in listOf(true, false)) {
            assertTrue("ALWAYS, hasRoom=$hasRoom", RowVisibility.ALWAYS.shows(hasRoom))
            assertFalse("NEVER, hasRoom=$hasRoom", RowVisibility.NEVER.shows(hasRoom))
            assertEquals(
                "WHEN_ROOM, hasRoom=$hasRoom",
                hasRoom,
                RowVisibility.WHEN_ROOM.shows(hasRoom),
            )
        }
    }

    @Test
    fun `when-room is what the old boolean meant`() {
        // The migration in Timer.fromJson rests on this: `true` was never "at every
        // size", it was "wherever the cell has room", which is exactly WHEN_ROOM.
        assertTrue(RowVisibility.WHEN_ROOM.shows(hasRoom = true))
        assertFalse(RowVisibility.WHEN_ROOM.shows(hasRoom = false))
    }
}
