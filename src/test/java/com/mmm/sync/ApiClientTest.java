package com.mmm.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ApiClientTest
{
    @Test
    void fallsBackOnlyForTheOfficialPrimarySyncEndpoint()
    {
        assertEquals(
                "https://www.mmmaniacs.com/api/mmm-sync",
                ApiClient.fallbackEndpoint("https://sync.mmmaniacs.com/v1/sync"));
        assertNull(ApiClient.fallbackEndpoint("https://sync.mmmaniacs.com/v1/other"));
        assertNull(ApiClient.fallbackEndpoint("https://example.com/v1/sync"));
        assertNull(ApiClient.fallbackEndpoint("not a uri"));
    }
}