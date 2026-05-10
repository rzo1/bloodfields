package com.github.rzo1.bloodfields.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class UpdateCheckerVersionCompareTest {

    @Test
    void semverPatchBumpIsNewer() {
        assumeFalse("dev".equals(BuildInfo.COMMIT), "dev build flips comparison; skip");
        assertTrue(UpdateChecker.isNewer("v1.0.1", "1.0.0"));
    }

    @Test
    void semverMajorBumpIsNewer() {
        assumeFalse("dev".equals(BuildInfo.COMMIT), "dev build flips comparison; skip");
        assertTrue(UpdateChecker.isNewer("v2.0.0", "1.9.9"));
    }

    @Test
    void shorterCurrentTreatedAsZeroPaddedNewer() {
        assumeFalse("dev".equals(BuildInfo.COMMIT), "dev build flips comparison; skip");
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.0"));
    }

    @Test
    void identicalSemverIsNotNewer() {
        assumeFalse("dev".equals(BuildInfo.COMMIT), "dev build flips comparison; skip");
        assertFalse(UpdateChecker.isNewer("v1.0.0", "1.0.0"));
    }

    @Test
    void olderTagIsNotNewer() {
        assumeFalse("dev".equals(BuildInfo.COMMIT), "dev build flips comparison; skip");
        assertFalse(UpdateChecker.isNewer("v1.0.0", "1.0.1"));
    }

    @Test
    void snapshotSuffixStrippedFromCurrent() {
        assumeFalse("dev".equals(BuildInfo.COMMIT), "dev build flips comparison; skip");
        assertTrue(UpdateChecker.isNewer("v1.1.0", "1.0.0-SNAPSHOT"));
        assertFalse(UpdateChecker.isNewer("v1.0.0", "1.0.0-SNAPSHOT"));
    }

    @Test
    void snapshotSuffixStrippedFromTag() {
        assumeFalse("dev".equals(BuildInfo.COMMIT), "dev build flips comparison; skip");
        assertFalse(UpdateChecker.isNewer("v1.0.0-SNAPSHOT", "1.0.0"));
    }

    @Test
    void unparseableReturnsFalse() {
        assumeFalse("dev".equals(BuildInfo.COMMIT), "dev build flips comparison; skip");
        assertFalse(UpdateChecker.isNewer("not-a-version", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("v1.0.0", "garbage"));
        assertFalse(UpdateChecker.isNewer(null, "1.0.0"));
    }

    @Test
    void parseSemverStripsVPrefixAndSnapshot() {
        int[] a = UpdateChecker.parseSemver("v1.2.3");
        int[] b = UpdateChecker.parseSemver("1.2.3-SNAPSHOT");
        assertTrue(a != null && a.length == 3 && a[0] == 1 && a[1] == 2 && a[2] == 3);
        assertTrue(b != null && b.length == 3 && b[0] == 1 && b[1] == 2 && b[2] == 3);
    }
}
