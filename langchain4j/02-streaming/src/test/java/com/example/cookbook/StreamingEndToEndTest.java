package com.example.cookbook;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the recipe's real SSE endpoint against a scripted model and reads it back the way a browser
 * does. The previous test only exercised the stub, which is how a no-op newline replacement in the
 * endpoint went unnoticed: nothing ever called the endpoint.
 */
class StreamingEndToEndTest {

    /** Tokens shaped like real model output: leading spaces, and newlines inside and at the end. */
    private static final List<String> TOKENS = List.of("Virtual", " threads", " are cheap.\n", "\nUse them", " for I/O.");

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private record Event(String name, String data) {
    }

    /** A minimal client-side parser, following the spec rather than the writer it is testing. */
    private static List<Event> parse(String body) {
        List<Event> events = new ArrayList<>();
        for (String block : body.split("\n\n")) {
            if (block.isBlank()) {
                continue;
            }
            String name = "message";
            List<String> data = new ArrayList<>();
            for (String line : block.split("\n", -1)) {
                if (line.startsWith("event:")) {
                    name = line.substring(6).strip();
                }
                else if (line.startsWith("data:")) {
                    String value = line.substring(5);
                    // The spec strips exactly one leading space, which is why " threads" survives.
                    data.add(value.startsWith(" ") ? value.substring(1) : value);
                }
            }
            events.add(new Event(name, String.join("\n", data)));
        }
        return events;
    }

    private static String get(int port) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/chat/stream?q=hi")).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    @Test
    void theClientReassemblesExactlyWhatTheModelProduced() throws Exception {
        StreamingChatModel scripted = new StreamingChatModel() {
            @Override
            public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
                TOKENS.forEach(handler::onPartialResponse);
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.from(String.join("", TOKENS))).build());
            }
        };
        server = Streaming.serveSse(scripted, 0);

        List<Event> events = parse(get(server.getAddress().getPort()));

        String received = events.stream()
                .filter(event -> event.name().equals("message"))
                .map(Event::data)
                .reduce("", String::concat);

        assertThat(received).isEqualTo(String.join("", TOKENS));
        assertThat(events.get(events.size() - 1).name()).isEqualTo("done");
    }

    @Test
    void anErrorArrivesAsAnErrorEventEvenWhenTheMessageSpansLines() throws Exception {
        StreamingChatModel failing = new StreamingChatModel() {
            @Override
            public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
                handler.onPartialResponse("partial");
                handler.onError(new IllegalStateException("upstream closed\nafter 1 token"));
            }
        };
        server = Streaming.serveSse(failing, 0);

        List<Event> events = parse(get(server.getAddress().getPort()));

        assertThat(events).extracting(Event::name).containsExactly("message", "error");
        assertThat(events.get(1).data()).isEqualTo("upstream closed\nafter 1 token");
    }
}
