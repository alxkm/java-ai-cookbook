package com.example.cookbook;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The only test here that talks to a real model. Skipped unless you ask for it:
 *
 *   COOKBOOK_LIVE=true AI_PROVIDER=ollama ./mvnw test
 *   COOKBOOK_LIVE=true OPENAI_API_KEY=sk-... ./mvnw test
 *
 * Order statuses are invented data - the model cannot know them from pre-training. If the answer
 * carries the right status, the tool really ran, which also proves the -parameters flag is on.
 */
@EnabledIfEnvironmentVariable(named = "COOKBOOK_LIVE", matches = "true")
class LiveModelTest {

    interface SupportAgent {

        @SystemMessage("You are a support agent. Use the tools instead of guessing. Be brief.")
        String answer(String question);
    }

    private SupportAgent agent() {
        return AiServices.builder(SupportAgent.class)
                .chatModel(Models.chat())
                .tools(new OrderTools())
                .build();
    }

    @Test
    void callsTheToolInsteadOfGuessing() {
        String answer = agent().answer("What is the status of order A-1001? Answer with the status word only.");

        assertThat(answer).isNotBlank().containsIgnoringCase("shipped");
    }

    @Test
    void reportsAnUnknownOrderRatherThanInventingOne() {
        String answer = agent().answer("What is the status of order A-9999?");

        assertThat(answer).isNotBlank();
        assertThat(answer.toLowerCase()).containsAnyOf("unknown", "not found", "no record", "does not exist");
    }
}
