package com.example.cookbook;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingTest {

    /** Emits a fixed token sequence, so the callback contract can be checked offline. */
    static class StubStreamingModel implements StreamingChatModel {
        @Override
        public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
            for (String token : List.of("vir", "tual ", "threads")) {
                handler.onPartialResponse(token);
            }
            handler.onCompleteResponse(
                    ChatResponse.builder().aiMessage(AiMessage.from("virtual threads")).build());
        }
    }

    @Test
    void deliversTokensThenCompletion() {
        List<String> tokens = new ArrayList<>();
        List<String> complete = new ArrayList<>();

        new StubStreamingModel().chat("anything", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
                tokens.add(token);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                complete.add(response.aiMessage().text());
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }
        });

        assertThat(tokens).containsExactly("vir", "tual ", "threads");
        assertThat(complete).containsExactly("virtual threads");
    }
}
