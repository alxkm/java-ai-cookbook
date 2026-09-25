package com.example.cookbook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SseTest {

    @Test
    void aSingleLineTokenIsOneDataField() {
        assertThat(Sse.data("hello")).isEqualTo("data: hello\n\n");
    }

    @Test
    void aTokenWithANewlineBecomesOneDataLinePerLine() {
        // The bug this recipe used to have: the newline was written into a single data field, and
        // the client ended the field there and dropped "second line" without a word.
        assertThat(Sse.data("first line\nsecond line"))
                .isEqualTo("data: first line\ndata: second line\n\n");
    }

    @Test
    void everyLineTerminatorTheSpecAcceptsIsSplitOn() {
        assertThat(Sse.data("a\r\nb\rc\nd")).isEqualTo("data: a\ndata: b\ndata: c\ndata: d\n\n");
    }

    @Test
    void aTrailingNewlineIsKeptRatherThanSwallowed() {
        // Tokens often end in a newline at a paragraph break; losing it glues paragraphs together.
        assertThat(Sse.data("end of paragraph\n")).isEqualTo("data: end of paragraph\ndata: \n\n");
    }

    @Test
    void namedEventsCarryTheNameBeforeTheData() {
        assertThat(Sse.event("done", "")).isEqualTo("event: done\ndata: \n\n");
    }

    @Test
    void aNullPayloadIsAnEmptyFieldNotTheWordNull() {
        assertThat(Sse.data(null)).isEqualTo("data: \n\n");
    }
}
