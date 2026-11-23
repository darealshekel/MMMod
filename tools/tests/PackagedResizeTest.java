package smoke;

// Isolated driver for the tick/resize state machine; no loader, window, or client.
final class PackagedResizeTest {
    public static void main(String[] args) {
        var wait = new PackagedRoutes.ResizeWait(680, 840, 0);
        require(!wait.settled(640, 360, 500_000_000L));
        require(!wait.settled(640, 360, 1_000_000_000L));
        require(!wait.retry(640, 1_000_000_000L));
        require(wait.retry(640, 3_000_000_000L));
        require(!wait.retry(640, 4_000_000_000L));
        require(!wait.settled(340, 360, 4_500_000_000L));
        require(!wait.settled(340, 420, 5_000_000_000L));
        require(wait.settled(340, 420, 5_500_000_000L));
        var stalled = new PackagedRoutes.ResizeWait(680, 840, 0);
        try {
            stalled.settled(640, 360, 12_000_000_000L);
        } catch (AssertionError expected) {
            require(expected.getMessage().contains("resize timed out"));
            return;
        }
        throw new AssertionError("stalled resize passed");
    }

    private static void require(boolean ok) {
        if (!ok) throw new AssertionError("bad resize transition");
    }
}

// Only the assertion and property API of the generated Fabric probe is needed here.
final class PackagedProbe {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static String prop(String name) { throw new AssertionError("unexpected client property: " + name); }
}
