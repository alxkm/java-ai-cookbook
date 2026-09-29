package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
class StreamingController {

    private final ChatClient chatClient;

    StreamingController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * WebFlux turns the Flux of tokens into server-sent events: the content type is what makes it a
     * stream on the wire. It splits multi-line tokens into one data line each, correctly. What it does
     * not do is protect a leading space - see onTheWire().
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<String> stream(@RequestParam("q") String question) {
        return chatClient.prompt().user(question).stream().content().map(StreamingController::onTheWire);
    }

    /**
     * Spring writes "data:" and then the text, with no space after the colon - on the first line and
     * after every line break it splits on. The SSE spec tells the client to drop exactly one leading
     * space from each data line, so a token that starts with a space loses it: " threads" arrives as
     * "threads" and a browser shows "Virtualthreads". With BPE tokenisation that is most tokens.
     *
     * One space in front of every line, so the space the client strips is this one and not the model's.
     */
    static String onTheWire(String token) {
        String normalised = token.replace("\r\n", "\n").replace('\r', '\n');
        return " " + normalised.replace("\n", "\n ");
    }
}
