package com.mmm.social;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

final class SocialSseReader {
    private static final int MAX_EVENT = 64 * 1024;

    private SocialSseReader() {}

    static void read(InputStream stream, BooleanSupplier active, BiConsumer<String, String> listener) throws IOException {
        Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
        StringBuilder line = new StringBuilder();
        StringBuilder data = new StringBuilder();
        String event = "";
        boolean afterCr = false;
        boolean first = true;
        int next;
        while (active.getAsBoolean() && (next = reader.read()) != -1) {
            char c = (char) next;
            if (first && c == '\uFEFF') { first = false; continue; }
            first = false;
            if (afterCr && c == '\n') { afterCr = false; continue; }
            afterCr = c == '\r';
            if (c != '\n' && c != '\r') {
                if (line.length() >= MAX_EVENT) throw new IOException("SSE line too large");
                line.append(c);
                continue;
            }
            String value = line.toString();
            line.setLength(0);
            if (value.isEmpty()) {
                if (data.length() > 0 && active.getAsBoolean()) {
                    listener.accept(event, data.substring(0, data.length() - 1));
                }
                event = "";
                data.setLength(0);
            } else {
                int colon = value.indexOf(':');
                String field = colon < 0 ? value : value.substring(0, colon);
                String content = colon < 0 ? "" : value.substring(colon + 1);
                if (content.startsWith(" ")) content = content.substring(1);
                if ("event".equals(field)) event = content;
                if ("data".equals(field)) {
                    if (data.length() + content.length() >= MAX_EVENT) throw new IOException("SSE event too large");
                    data.append(content).append('\n');
                }
            }
        }
    }
}
