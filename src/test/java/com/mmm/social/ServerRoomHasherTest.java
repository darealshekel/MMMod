package com.mmm.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class ServerRoomHasherTest
{
    @Test
    void normalizesAddressCasingAndWhitespace()
    {
        assertEquals(ServerRoomHasher.hash("play.example.com:25565"), ServerRoomHasher.hash("  PLAY.EXAMPLE.COM:25565  "));
        assertEquals(ServerRoomHasher.hash("play.example.com"), ServerRoomHasher.hash("play.example.com:25565"));
        assertEquals(ServerRoomHasher.hash("play.example.com."), ServerRoomHasher.hash("play.example.com"));
    }

    @Test
    void doesNotExposeTheServerAddress()
    {
        String roomId = ServerRoomHasher.hash("play.example.com:25565");
        assertEquals(64, roomId.length());
        assertFalse(roomId.contains("example"));
        assertNotEquals(ServerRoomHasher.hash("other.example.com:25565"), roomId);
    }
}
