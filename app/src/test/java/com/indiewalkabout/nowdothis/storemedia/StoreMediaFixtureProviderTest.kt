package com.indiewalkabout.nowdothis.storemedia

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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

    @Test
    fun emulatorIdentity_rejectsPhysicalDevices() {
        assertTrue(
            isStoreMediaEmulator(
                hardware = "ranchu",
                fingerprint = "google/sdk_gphone64_arm64/emu64a:16/test-keys",
                model = "sdk_gphone64_arm64",
                product = "sdk_gphone64_arm64"
            )
        )
        assertTrue(
            isStoreMediaEmulator(
                hardware = "goldfish",
                fingerprint = "generic/sdk/generic:13/test-keys",
                model = "Android SDK built for x86",
                product = "sdk"
            )
        )
        assertFalse(
            isStoreMediaEmulator(
                hardware = "qcom",
                fingerprint = "vendor/phone/device:14/release-keys",
                model = "Physical phone",
                product = "device"
            )
        )
    }

    @Test
    fun missingOrUnsupportedLocale_isRejectedBeforeAnyMutation() {
        listOf(null, "fr-FR").forEach { localeTag ->
            var sortReset = false
            var fixturePrepared = false

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    prepareStoreMediaFixture(
                        localeTag = localeTag,
                        resetTaskSort = { sortReset = true },
                        prepareFixture = { fixturePrepared = true }
                    )
                }
            }

            assertFalse("sort reset for locale $localeTag", sortReset)
            assertFalse("fixture prepared for locale $localeTag", fixturePrepared)
        }
    }
}
