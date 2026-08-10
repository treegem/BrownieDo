package eu.sweetgeorgie.browniedo.data.user

import eu.sweetgeorgie.browniedo.domain.user.Partner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserMapperTest {

    @Test
    fun `maps a complete document onto the domain model`() {
        val document = UserDocument(displayName = "Anna")

        assertEquals(Partner(uid = UID, displayName = "Anna"), document.toPartner(UID))
    }

    @Test
    fun `rejects a document without a display name`() {
        assertNull(UserDocument(displayName = "  ").toPartner(UID))
    }

    private companion object {
        const val UID = "uid-2"
    }
}
