package com.example.cookbook;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads the controller's raw event stream and parses it the way a browser's EventSource does. The
 * other test only checked the ChatClient's token Flux, which is correct - the damage happened after
 * it, on the wire, where nothing looked.
 */
class StreamingWireTest {

    /** Shaped like real model output: most tokens start with a space, and some carry newlines. */
    private static final List<String> TOKENS = List.of("Virtual", " threads", " are cheap.\n", "\nUse", " them", "  indented");

    private static String rawStream(List<String> tokens) {
        ChatModel scripted = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.fromIterable(tokens)
                        .map(token -> new ChatResponse(List.of(new Generation(new AssistantMessage(token)))));
            }
        };

        byte[] body = WebTestClient.bindToController(new StreamingController(ChatClient.builder(scripted).build()))
                .build()
                .get().uri("/chat/stream?q=hi")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectBody(byte[].class).returnResult().getResponseBody();
        return new String(body, StandardCharsets.UTF_8);
    }

    /** The client side of the spec, written from the spec rather than from the writer under test. */
    private static List<String> parse(String stream) {
        List<String> events = new ArrayList<>();
        for (String block : stream.split("\n\n")) {
            List<String> data = new ArrayList<>();
            for (String line : block.split("\n", -1)) {
                if (line.startsWith("data:")) {
                    String value = line.substring(5);
                    // The step that bites: exactly one leading space after the colon is dropped.
                    data.add(value.startsWith(" ") ? value.substring(1) : value);
                }
            }
            if (!data.isEmpty()) {
                events.add(String.join("\n", data));
            }
        }
        return events;
    }

    @Test
    void aBrowserReassemblesExactlyWhatTheModelProduced() {
        assertThat(String.join("", parse(rawStream(TOKENS)))).isEqualTo(String.join("", TOKENS));
    }

    @Test
    void theSpaceThatStartsATokenSurvives() {
        // " threads" is the common case, not the edge case: with BPE tokenisation most tokens begin
        // with the space that separates them from the previous word. Lose it and a browser shows
        // "Virtualthreads".
        assertThat(parse(rawStream(List.of("Virtual", " threads")))).containsExactly("Virtual", " threads");
    }
}
