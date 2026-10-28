package com.mmm.sync;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProfileRequestIdentityTest {
    @Test void accountSwitchAndRelinkRejectOldRequestsAndResetThrottle() {
        var requests = new ProfileRequestIdentity();
        long a = requests.begin("a|link1", 100, false);
        assertTrue(a > 0);
        assertEquals(0, requests.begin("a|link1", 101, true));
        long b = requests.begin("b|link1", 102, false);
        assertTrue(b > a);
        assertFalse(requests.finish(a, "b|link1"));
        assertTrue(requests.finish(b, "b|link1"));
        assertEquals(0, requests.begin("b|link1", 103, false));
        long relink = requests.begin("b|link2", 104, false);
        assertTrue(relink > b);
        assertFalse(requests.finish(relink, "b|link3"));
    }
}
