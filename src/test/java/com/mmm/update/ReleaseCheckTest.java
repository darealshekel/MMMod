package com.mmm.update;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReleaseCheckTest
{
    private static final String CATALOG = """
            {"downloads":[{"minecraftVersion":"1.21","modVersion":"1.0.19"},
            {"minecraftVersion":"1.21.1","modVersion":"1.0.30"},
            {"minecraftVersion":"1.21.4","modVersion":"1.0.18.1"}]}
            """;

    @Test void findsUpdateOnlyForExactMinecraftVersion() {
        assertEquals("1.0.19", ReleaseCheck.newerVersion(CATALOG, "1.21", "1.0.18+1.21").orElseThrow());
        assertTrue(ReleaseCheck.newerVersion(CATALOG, "1.21.11", "1.0.18").isEmpty());
    }

    @Test void doesNotSuggestDowngradesOrCurrentRelease() {
        assertTrue(ReleaseCheck.newerVersion(CATALOG, "1.21", "1.0.19+1.21").isEmpty());
        assertTrue(ReleaseCheck.newerVersion(CATALOG, "1.21", "1.0.20+1.21").isEmpty());
    }

    @Test void handlesFourPartVersionsAndNumericComparison() {
        assertEquals("1.0.18.1", ReleaseCheck.newerVersion(CATALOG, "1.21.4", "1.0.18").orElseThrow());
        assertEquals("1.0.19", ReleaseCheck.newerVersion(CATALOG, "1.21", "1.0.9").orElseThrow());
        assertTrue(ReleaseCheck.newerVersion(CATALOG, "1.21", "1.0.19.0").isEmpty());
    }

    @Test void ignoresUnknownVersionsAndNonStableCandidates() {
        assertTrue(ReleaseCheck.newerVersion(CATALOG, "1.21", "unknown").isEmpty());
        String beta = CATALOG.replace("1.0.19", "1.0.21-beta");
        assertTrue(ReleaseCheck.newerVersion(beta, "1.21", "1.0.20").isEmpty());
        assertTrue(ReleaseCheck.newerVersion("{}", "1.21", "1.0.18").isEmpty());
    }
}
