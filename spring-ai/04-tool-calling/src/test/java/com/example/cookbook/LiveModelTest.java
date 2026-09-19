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
 * Order statuses are invented data - the model cannot know them from pre-training. If the answer
 * carries the right status, the tool really ran.
 */
@EnabledIfEnvironmentVariable(named = "COOKBOOK_LIVE", matches = "true")
@SpringBootTest
@ActiveProfiles("test")
class LiveModelTest {

    @Autowired
    ChatClient chatClient;

    private String ask(String question) {
        return chatClient.prompt().user(question).tools(new OrderTools()).call().content();
    }

    @Test
    void callsTheToolInsteadOfGuessing() {
        String answer = ask("What is the status of order A-1001? Answer with the status word only.");

        assertThat(answer).isNotBlank();
        assertThat(answer).containsIgnoringCase("shipped");
    }

    @Test
    void reportsAnUnknownOrderRatherThanInventingOne() {
        String answer = ask("What is the status of order A-9999?");

        assertThat(answer).isNotBlank();
        assertThat(answer.toLowerCase()).containsAnyOf("unknown", "not found", "no record", "does not exist");
    }
}
