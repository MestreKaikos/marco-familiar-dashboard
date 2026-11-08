package com.lito.planify.dashboard.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quan una foto de Drive canvia però conserva nom i ID, cal substituir la còpia local. */
class FamilyPhotoCacheRevisionTest {
    @Test fun newFileAlwaysDownloads() {
        assertTrue(photoCacheNeedsDownload(false, "", "2026-11-08T15:30:37.743Z"))
    }

    @Test fun unchangedDriveVersionReusesLocalFile() {
        val revision = "2026-11-08T15:30:37.743Z"
        assertFalse(photoCacheNeedsDownload(true, revision, revision))
    }

    @Test fun sameDriveIdWithNewRevisionDownloadsAgain() {
        assertTrue(photoCacheNeedsDownload(true, "2026-11-07T15:30:37.743Z",
            "2026-11-08T15:30:37.743Z"))
    }

    @Test fun oldCacheWithoutRevisionIsMigrated() {
        assertTrue(photoCacheNeedsDownload(true, "", "2026-11-08T15:30:37.743Z"))
    }

    @Test fun olderAppsScriptWithoutRevisionRemainsCompatible() {
        assertFalse(photoCacheNeedsDownload(true, "", ""))
    }

    @Test fun damagedCachedFileIsAlwaysReplaced() {
        assertTrue(photoCacheNeedsDownload(false, "2026-11-08T15:30:37.743Z",
            "2026-11-08T15:30:37.743Z"))
    }
}
