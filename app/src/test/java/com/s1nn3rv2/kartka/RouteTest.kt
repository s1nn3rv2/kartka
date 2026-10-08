package com.s1nn3rv2.kartka

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteTest {
    // The back stack is saved as these strings; paths can contain ':' and must survive a round trip.
    @Test fun roundTrip() {
        for (r in listOf(Route.Home, Route.Settings, Route.Trash, Route.Scratch, Route.Folder("Recipes/Ca:kes"), Route.Tag("zdrowie/silownia"), Route.Note("a:b/c.md"))) {
            assertEquals(r, Route.decode(r.encode()))
        }
        // A restored new note opens like an existing one: its title focus already happened.
        assertEquals(Route.Note("x.md"), Route.decode(Route.Note("x.md", isNew = true).encode()))
    }
}
