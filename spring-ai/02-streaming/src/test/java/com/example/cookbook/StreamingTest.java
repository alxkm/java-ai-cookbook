package com.example.cookbook;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

class StreamingTest {

    /** Emits a fixed token sequence so the streaming path can be checked without a key. */
    static class StubChatModel implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            return chunk("virtual threads");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(chunk("vir"), chunk("tual "), chunk("threads"));
        }

        private ChatResponse chunk(String text) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        }
    }

    @Test
    void streamsTokensInOrder() {
        ChatClient client = ChatClient.builder(new StubChatModel()).build();

        StepVerifier.create(client.prompt().user("anything").stream().content())
                .expectNext("vir", "tual ", "threads")
                .verifyComplete();
    }
}
