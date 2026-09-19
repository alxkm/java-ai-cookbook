package com.example.cookbook;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No API key, no network: a stub ChatModel is enough to prove the wiring compiles and
 * that the system prompt reaches the model.
 */
class ChatBasicTest {

    @Test
    void sendsSystemPromptAndUserMessage() {
        var captured = new java.util.concurrent.atomic.AtomicReference<Prompt>();

        ChatModel stub = prompt -> {
            captured.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("42"))));
        };

        ChatClient client = ChatClient.builder(stub)
                .defaultSystem("You are a terse Java assistant.")
                .build();

        String answer = client.prompt().user("How many?").call().content();

        assertThat(answer).isEqualTo("42");
        assertThat(captured.get().getInstructions()).hasSize(2);
        assertThat(captured.get().getInstructions().get(0).getText()).contains("terse Java assistant");
        assertThat(captured.get().getInstructions().get(1).getText()).isEqualTo("How many?");
    }
}
