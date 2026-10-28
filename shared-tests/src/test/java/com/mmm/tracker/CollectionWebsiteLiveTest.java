package com.mmm.tracker;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "MMM_COLLECTIONS_LIVE", matches = "true")
class CollectionWebsiteLiveTest
{
    @Test void loadsWebsiteWorldsAndReusesCachedRequest() throws Exception {
        var request = CollectionWebsiteClient.load("live-test-5hekel", "5hekel");
        assertSame(request, CollectionWebsiteClient.load("live-test-5hekel", "5hekel"));
        var result = request.get(25, TimeUnit.SECONDS);
        assertTrue(result.worlds() > 1);
        assertTrue(result.counts().getOrDefault("minecraft:stone", 0L) > 0);
        System.out.println("COLLECTIONS LIVE: " + result.worlds() + " worlds, " + result.counts().size() + " block types");
    }
}
