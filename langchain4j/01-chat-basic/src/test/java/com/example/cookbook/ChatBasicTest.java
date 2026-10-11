package com.example.cookbook;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Goes through the recipe's own ask(...) and usage(...). The earlier test called the stub with a
 * system prompt of its own - a shorter one than the recipe sends - so it checked neither the prompt
 * nor the code that prints the result.
 */
class ChatBasicTest {

    @Test
    void sendsTheRecipesSystemPromptThenTheQuestion() {
        StubChatModel model = new StubChatModel("42");

        String answer = ChatBasic.ask(model, "How many?").aiMessage().text();

        assertThat(answer).isEqualTo("42");
        assertThat(model.requests).hasSize(1);
        assertThat(model.requests.get(0).messages()).containsExactly(
                SystemMessage.from(ChatBasic.SYSTEM_PROMPT),
                UserMessage.from("How many?"));
        // The length limit is the instruction that does the work, and the one a copy left out.
        assertThat(ChatBasic.SYSTEM_PROMPT).contains("at most three sentences");
    }

    @Test
    void reportsTheTokensWhenTheProviderSendsThem() {
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("42"))
                .tokenUsage(new TokenUsage(12, 3))
                .build();

        assertThat(ChatBasic.usage(response)).isEqualTo("15 tokens");
    }

    @Test
    void aProviderThatSendsNoUsageIsNotACrash() {
        // The stub in this very test suite is such a provider, which is how the unguarded read in
        // main() would have shown up had any test called it.
        ChatResponse response = ChatBasic.ask(new StubChatModel("42"), "How many?");

        assertThat(response.tokenUsage()).isNull();
        assertThat(ChatBasic.usage(response)).isEqualTo("usage not reported");
    }
}
