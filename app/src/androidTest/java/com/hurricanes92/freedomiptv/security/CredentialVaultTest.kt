package com.hurricanes92.freedomiptv.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialVaultTest {
    private lateinit var vault: CredentialVault

    @Before fun setUp() {
        vault = CredentialVault(InstrumentationRegistry.getInstrumentation().targetContext)
        vault.clearPassword()
    }

    @After fun tearDown() {
        vault.clearPassword()
    }

    @Test fun encryptedPasswordCanBeSavedAndRestored() {
        assertNull(vault.getPassword())
        assertEquals(true, vault.savePassword("test-secret-123"))
        assertEquals("test-secret-123", vault.getPassword())
    }

    @Test fun replacingPasswordRestoresLatestValue() {
        assertEquals(true, vault.savePassword("old-secret"))
        assertEquals(true, vault.savePassword("new-secret"))
        assertEquals("new-secret", vault.getPassword())
    }

    @Test fun logoutClearsSavedPassword() {
        assertEquals(true, vault.savePassword("logout-secret"))
        assertEquals(true, vault.clearPassword())
        assertNull(vault.getPassword())
    }
}
