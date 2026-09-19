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
     * WebFlux turns the Flux of tokens into server-sent events. No extra plumbing:
     * the content type is what makes it a stream on the wire.
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<String> stream(@RequestParam("q") String question) {
        return chatClient.prompt().user(question).stream().content();
    }
}
