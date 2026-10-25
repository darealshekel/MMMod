package com.mmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldIdentityTest
{
    @Test
    void rawLegacyHostAndCurrentHashResolveToOneServer()
    {
        String canonical = WorldIdentity.multiplayerWorldId("smp.cosymc.win");

        assertEquals("server_dffdebc6ee0e", canonical);
        assertEquals(
                canonical,
                WorldIdentity.canonicalWorldId("smp.cosymc.win", "multiplayer", "smp.cosymc.win"));
        assertTrue(WorldIdentity.matchesCurrentWorld(
                "smp.cosymc.win",
                canonical,
                "multiplayer",
                "smp.cosymc.win"));
    }

    @Test
    void differentServersRemainSeparate()
    {
        String canonical = WorldIdentity.multiplayerWorldId("smp.cosymc.win");

        assertFalse(WorldIdentity.matchesCurrentWorld(
                "play.somewhere-else.example",
                canonical,
                "multiplayer",
                "smp.cosymc.win"));
    }
}
