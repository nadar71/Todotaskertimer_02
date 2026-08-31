package com.indiewalkabout.nowdothis.storemedia

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreMediaFixtureProviderTest {
    @Test
    fun fixtureCaller_allowsOnlyApplicationAndShellUids() {
        val appUid = 10_321
        val shellUid = 2_000

        assertTrue(isStoreMediaFixtureCaller(appUid, appUid, shellUid))
        assertTrue(isStoreMediaFixtureCaller(shellUid, appUid, shellUid))
        assertFalse(isStoreMediaFixtureCaller(10_777, appUid, shellUid))
    }
}
