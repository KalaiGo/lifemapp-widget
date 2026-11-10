package com.kalai73.lifemapp.widgets.ui

import com.kalai73.lifemapp.widgets.core.model.CachedState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRulesTest {
    @Test
    fun `the second login step comes before the unlock and home`() {
        assertEquals(Screen.SECOND_STEP, screenFor(signedIn = true, encryptionEnabled = true, unlocked = false, unlockSkipped = false, awaitingSecondStep = true))
        assertEquals(Screen.SECOND_STEP, screenFor(signedIn = true, encryptionEnabled = false, unlocked = true, unlockSkipped = true, awaitingSecondStep = true))
        assertEquals(Screen.SIGN_IN, screenFor(signedIn = false, encryptionEnabled = false, unlocked = false, unlockSkipped = false, awaitingSecondStep = true))
    }

    @Test
    fun `signed out always shows sign-in`() {
        assertEquals(Screen.SIGN_IN, screenFor(signedIn = false, encryptionEnabled = true, unlocked = false, unlockSkipped = false))
        assertEquals(Screen.SIGN_IN, screenFor(signedIn = false, encryptionEnabled = false, unlocked = true, unlockSkipped = true))
    }

    @Test
    fun `an encrypted account asks to unlock until unlocked or skipped`() {
        assertEquals(Screen.UNLOCK, screenFor(signedIn = true, encryptionEnabled = true, unlocked = false, unlockSkipped = false))
        assertEquals(Screen.HOME, screenFor(signedIn = true, encryptionEnabled = true, unlocked = true, unlockSkipped = false))
        assertEquals(Screen.HOME, screenFor(signedIn = true, encryptionEnabled = true, unlocked = false, unlockSkipped = true))
    }

    @Test
    fun `an unencrypted account goes straight home`() {
        assertEquals(Screen.HOME, screenFor(signedIn = true, encryptionEnabled = false, unlocked = false, unlockSkipped = false))
    }

    @Test
    fun `opening the app syncs a snapshot older than the threshold`() {
        val now = 10 * OPEN_SYNC_AFTER_MS
        assertTrue(shouldSyncOnOpen(CachedState(lastSuccessAt = now - OPEN_SYNC_AFTER_MS), now))
        assertTrue(shouldSyncOnOpen(CachedState(), now))
        assertFalse(shouldSyncOnOpen(CachedState(lastSuccessAt = now - OPEN_SYNC_AFTER_MS + 1), now))
    }

    @Test
    fun `opening the app doesn't sync while a sync runs, but does once it has timed out`() {
        val now = 10 * OPEN_SYNC_AFTER_MS
        assertFalse(shouldSyncOnOpen(CachedState(syncing = true, syncStartedAt = now - 1_000), now))
        assertTrue(shouldSyncOnOpen(CachedState(syncing = true, syncStartedAt = now - CachedState.SYNC_TIMEOUT_MS), now))
    }
}
