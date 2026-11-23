package com.mmm.social;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Diagnostic characterization, not a claim that the reported game crash is reproduced. */
class AfkReviewTest {
    @Test void rateLimitedPresenceDoesNotRetryEveryIdleSecond() {
        var now = new AtomicLong(1000);
        var transport = new SocialSessionTest.FakeTransport();
        var sink = new SocialSessionTest.FakeSink();
        var session = new SocialSession(transport, sink, now::get);
        sink.identity = new SocialSession.Identity("uuid", "client", "test-token", new Object());
        session.identify(sink.identity);
        session.tick(false);
        transport.listeners.getFirst().opened();
        for (int second = 0; second < 60; second++) {
            session.tick(false);
            transport.posts.getLast().future().complete(new SocialHttp.Response(429, "{}"));
            now.addAndGet(1000);
        }
        assertEquals(1, transport.posts.size());
        assertTrue(transport.posts.stream().allMatch(post -> post.path().equals("/presence")));
        session.disconnect();
    }
}
