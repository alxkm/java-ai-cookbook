package com.example.cookbook;

import java.util.regex.Pattern;

/**
 * Server-sent event framing, which is the part of this recipe that is easy to get subtly wrong.
 *
 * A data field cannot contain a line break. The spec's answer is one "data:" line per line of the
 * payload, and the client joins them back with "\n". Write the break into a single field instead
 * and the client ends the field there: everything after the first newline in a token is dropped,
 * silently, and model output is full of newlines - paragraphs, lists, code.
 *
 * Spring's side of this recipe never has the problem, because WebFlux encodes a Flux of strings
 * with text/event-stream and does the splitting itself. Hand-rolling SSE means owning it.
 */
final class Sse {

    /** The spec accepts CRLF, CR and LF alike as a line terminator, so all three are split on. */
    private static final Pattern LINE_BREAK = Pattern.compile("\r\n|\r|\n");

    private Sse() {
    }

    static String data(String payload) {
        return event(null, payload);
    }

    static String event(String name, String payload) {
        StringBuilder frame = new StringBuilder();
        if (name != null) {
            frame.append("event: ").append(name).append('\n');
        }
        // split with -1 keeps trailing empty strings, so a token that ends in a newline still
        // carries that newline to the client instead of losing it at the end of the field.
        for (String line : LINE_BREAK.split(payload == null ? "" : payload, -1)) {
            frame.append("data: ").append(line).append('\n');
        }
        return frame.append('\n').toString();
    }
}
