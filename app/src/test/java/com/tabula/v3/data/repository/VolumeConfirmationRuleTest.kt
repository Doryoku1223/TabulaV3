package com.tabula.v3.data.repository

import org.junit.Assert.*
import org.junit.Test

class VolumeConfirmationRuleTest {
    private val primary = setOf("external_primary")

    @Test fun recentListMayContainTheCurrentlyMountedPrimaryVolume() {
        assertTrue(canConfirmMissingOnVolume("external", true, primary, primary))
    }

    @Test fun allRecentVolumesAlreadyMountedDoNotBlockMergedUri() {
        val mounted = setOf("external_primary", "mounted-usb")
        assertTrue(canConfirmMissingOnVolume("external", true, mounted, mounted))
    }

    @Test fun genuinelyUnmountedRecentVolumeStillBlocksAmbiguousMergedUri() {
        assertFalse(canConfirmMissingOnVolume("external", true, primary, primary + "ejected-sd"))
    }

    @Test fun mergedUriRequiresConsistentMountedPrimaryEvidence() {
        assertFalse(canConfirmMissingOnVolume("external", false, primary, primary))
        assertFalse(canConfirmMissingOnVolume("external", true, emptySet(), emptySet()))
    }

    @Test fun concretePrimaryIsNotBlockedByAnUnrelatedEjectedVolume() {
        assertTrue(canConfirmMissingOnVolume("external_primary", true, primary, primary + "ejected-sd"))
    }

    @Test fun concreteUnavailableVolumesCannotBeConfirmedMissing() {
        assertFalse(canConfirmMissingOnVolume("external_primary", true, emptySet(), emptySet()))
        assertFalse(canConfirmMissingOnVolume("external_primary", false, primary, primary))
        assertFalse(canConfirmMissingOnVolume("removed-sd", true, primary, primary + "removed-sd"))
    }
}
