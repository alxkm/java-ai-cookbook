package com.example.cookbook;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No API key, no network: a stub ChatModel is enough to prove the system prompt reaches the model.
 *
 * The ChatClient comes from the recipe's own chatClient(...) bean method. The earlier test built a
 * client of its own with a shorter system prompt than the recipe's, so it would have passed whatever
 * the recipe configured.
 */
class ChatBasicTest {

    @Test
    void sendsTheRecipesSystemPromptThenTheQuestion() {
        AtomicReference<Prompt> captured = new AtomicReference<>();
        ChatModel stub = prompt -> {
            captured.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("42"))));
        };

        ChatClient client = new ChatBasicApplication().chatClient(ChatClient.builder(stub));
        String answer = client.prompt().user("How many?").call().content();

        assertThat(answer).isEqualTo("42");
        assertThat(captured.get().getInstructions()).hasSize(2);
        assertThat(captured.get().getInstructions().get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        // The length limit is the instruction that does the work, and the one the copy left out.
        assertThat(captured.get().getInstructions().get(0).getText()).contains("at most three sentences");
        assertThat(captured.get().getInstructions().get(1).getText()).isEqualTo("How many?");
    }
}
