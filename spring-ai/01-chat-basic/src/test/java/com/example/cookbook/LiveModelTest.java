package com.example.cookbook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
@SpringBootTest
@ActiveProfiles("test")
class LiveModelTest {

    @Autowired
    ChatClient chatClient;

    @Test
    void reachesTheProvider() {
        String answer = chatClient.prompt().user("What is a virtual thread?").call().content();

        assertThat(answer).isNotBlank();
    }

    @Test
    void theSystemPromptIsHonoured() {
        // The bean is built with "Answer in at most three sentences." Long answers mean the
        // default system prompt is not reaching the provider.
        String answer = chatClient.prompt().user("What is a virtual thread?").call().content();

        assertThat(answer).isNotBlank();
        assertThat(answer.split("[.!?]\\s").length).isLessThanOrEqualTo(6);
    }
}
