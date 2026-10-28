package com.mmm.social;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SocialSseReaderTest {
    private List<String> read(String input) throws IOException {
        List<String> received = new ArrayList<>();
        SocialSseReader.read(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), () -> true,
                (name, data) -> received.add(name + ":" + data));
        return received;
    }
    @Test void handlesBomHeartbeatMultilineAndAllLineEndings() throws Exception {
        assertEquals(List.of("chat:{\n\"message\":\"hello\"}", "presence:{}"), read(
                "\uFEFF: heartbeat\r\nevent: chat\r\ndata: {\r\ndata: \"message\":\"hello\"}\r\n\r\n"
                        + "event: presence\rdata:{}\r\r"));
    }
    @Test void dropsUnterminatedEventAndIgnoresIdAndComments() throws Exception {
        assertTrue(read("event: chat\ndata: {}\n").isEmpty());
        assertEquals(List.of("chat: {}"), read("id:1\n: comment\nevent:chat\ndata:  {}\n\n"));
    }
    @Test void boundsBothLinesAndAccumulatedEvents() {
        assertThrows(IOException.class, () -> read("data:" + "x".repeat(70_000)));
        assertThrows(IOException.class, () -> read(("data:" + "x".repeat(1000) + "\n").repeat(70)));
    }
    @Test void cancellationDoesNotDeliverAlreadyBufferedData() throws Exception {
        List<String> result = new ArrayList<>();
        SocialSseReader.read(new ByteArrayInputStream("data: {}\n\n".getBytes(StandardCharsets.UTF_8)), () -> false,
                (name, data) -> result.add(data));
        assertTrue(result.isEmpty());
    }
}
