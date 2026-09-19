package com.example.cookbook;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The only test here that talks to a real model. Skipped unless you ask for it:
 *
 *   COOKBOOK_LIVE=true AI_PROVIDER=ollama ./mvnw test
 *   COOKBOOK_LIVE=true OPENAI_API_KEY=sk-... ./mvnw test
 *
 * It asserts shape, not wording. A test that expects a model to say a particular sentence is a
 * test that fails on a Tuesday for no reason - that job belongs to the eval suite in recipe 17.
 */
@EnabledIfEnvironmentVariable(named = "COOKBOOK_LIVE", matches = "true")
class LiveModelTest {

    @Test
    void reachesTheProviderAndReportsTokenUsage() {
        ChatModel model = Models.chat();

        ChatResponse response = model.chat(
                SystemMessage.from("You are a terse Java assistant."),
                UserMessage.from("What is a virtual thread?"));

        assertThat(response.aiMessage().text()).isNotBlank();

        // The whole reason to use chat(ChatMessage...) rather than chat(String) is this metadata.
        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.tokenUsage().inputTokenCount()).isPositive();
        assertThat(response.tokenUsage().outputTokenCount()).isPositive();
        assertThat(response.finishReason()).isNotNull();
    }

    @Test
    void theSystemPromptReachesTheModel() {
        ChatModel model = Models.chat();

        // Not a knowledge check - a wiring check. If the system message were dropped, the model
        // would have no reason to answer with one word.
        String answer = model.chat(
                SystemMessage.from("Reply with exactly one word: OK. Nothing else."),
                UserMessage.from("Are you there?")).aiMessage().text();

        assertThat(answer).isNotBlank();
        assertThat(answer.trim().split("\\s+")).hasSizeLessThanOrEqualTo(3);
    }
}
