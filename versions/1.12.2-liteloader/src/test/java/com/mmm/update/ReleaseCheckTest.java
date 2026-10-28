package com.mmm.update;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReleaseCheckTest {
    private String release(String game, String version) {
        return "{\"minecraftVersion\":\"" + game + "\",\"modVersion\":\"" + version + "\"}";
    }

    @Test void choosesNewestStableForExactly1122() {
        String json = "{\"downloads\":[" + release("1.21", "99.0.0") + "," + release("1.12.2", "1.0.21")
                + "," + release("1.12.2", "1.0.100") + "," + release("1.12.2", "2.0.0-beta") + "]}";
        assertEquals("1.0.100", ReleaseCheck.newerVersion(json, "1.12.2", "1.0.20-dev+1.12.2-liteloader").orElseThrow());
    }

    @Test void currentAndOlderReleasesProduceNoNotice() {
        assertTrue(ReleaseCheck.newerVersion("{\"downloads\":[" + release("1.12.2", "1.0.20.0") + "]}",
                "1.12.2", "1.0.20+1.12.2").isEmpty());
        assertTrue(ReleaseCheck.newerVersion("{\"downloads\":[" + release("1.12.2", "1.0.19") + "]}",
                "1.12.2", "1.0.20").isEmpty());
    }

    @Test void malformedEntriesDoNotHideValidRelease() {
        String json = "{\"downloads\":[null,5,{}, {\"minecraftVersion\":[],\"modVersion\":null},"
                + release("1.12.2", "1.0.21") + "]}";
        assertEquals("1.0.21", ReleaseCheck.newerVersion(json, "1.12.2", "1.0.20").orElseThrow());
    }

    @Test void malformedOrOversizedResponseAndUnsafeVersionsAreIgnored() {
        for (String json : new String[]{"garbage", "[]", "null", "{\"downloads\":{}}", "x".repeat(1_048_577)})
            assertTrue(ReleaseCheck.newerVersion(json, "1.12.2", "1.0.20").isEmpty());
        assertTrue(ReleaseCheck.newerVersion("{\"downloads\":[" + release("1.12.2", "https://bad.example") + "]}",
                "1.12.2", "1.0.20").isEmpty());
    }
}
