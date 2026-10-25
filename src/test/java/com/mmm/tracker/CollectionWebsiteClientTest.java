package com.mmm.tracker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectionWebsiteClientTest
{
    @Test void includesWorldsMissingFromLocalInstanceAndDeduplicatesSourceAliases() {
        var snapshot = CollectionWebsiteClient.parse("""
                {"blockBreakdowns":[
                  {"worldId":"a","sourceKey":"cosy","items":[{"blockId":"minecraft:stone","count":100}]},
                  {"worldId":"b","sourceKey":"redtech","items":[{"blockId":"minecraft:stone","count":200},
                    {"blockId":"minecraft:diamond_ore","count":25}]},
                  {"worldId":"old-b","sourceKey":"redtech","items":[{"blockId":"minecraft:stone","count":190}]}
                ]}
                """);
        assertEquals(2, snapshot.worlds());
        assertEquals(300L, snapshot.counts().get("minecraft:stone"));
        assertEquals(25L, snapshot.counts().get("minecraft:diamond_ore"));
    }

    @Test void incompleteBreakdownsAreNotCachedAsCompleteOrZero() {
        assertThrows(IllegalStateException.class, () -> CollectionWebsiteClient.parse("""
                {"blockBreakdowns":[],"_profileIncompleteFields":["blockBreakdowns"]}
                """));
        assertThrows(IllegalStateException.class, () -> CollectionWebsiteClient.parse("{}"));
        assertEquals(0, CollectionWebsiteClient.parse("{\"blockBreakdowns\":[]}").worlds());
        assertEquals(0, CollectionWebsiteClient.parse("""
                {"blockBreakdowns":[],"_profileIncompleteFields":["sessions"]}
                """).worlds());
    }

    @Test void rejectsMalformedCountsAndSupportsWorldIdFallback() {
        String template = """
                {"blockBreakdowns":[{"worldId":"a","items":[{"blockId":"minecraft:stone","count":%s}]}]}
                """;
        for (String count : new String[] {"-1", "1.5", "9223372036854775808"})
            assertThrows(RuntimeException.class, () -> CollectionWebsiteClient.parse(template.formatted(count)));
        assertEquals(50L, CollectionWebsiteClient.parse(template.formatted("50")).counts().get("minecraft:stone"));
    }
}
