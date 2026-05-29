package com.citizeneye.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeputyPhotoTest {
    @Test fun buildsOfficialRoundedPortraitUrlFromActorId() {
        assertEquals(
            "https://www.assemblee-nationale.fr/dyn/static/tribun/17/photos/carre/841605.jpg",
            deputyPhotoUrl("PA841605")
        )
    }

    @Test fun ignoresUnexpectedActorIdsForPhotos() {
        assertNull(deputyPhotoUrl(""))
        assertNull(deputyPhotoUrl("not-an-actor"))
    }
}
