package com.example.cookbook;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provider switching is the promise on the front page of this repo, and it is the part most
 * likely to break silently: a starter auto-configures every model type it knows - embedding,
 * image, speech, transcription, moderation - and each one wants a key at startup. An unrelated
 * bean can fail a run that never touches it.
 *
 * These tests boot the real application context with the keys a user would actually have, one
 * provider at a time, and assert that the wired ChatModel is the one that was asked for.
 * No model is called and no key is real.
 */
class ProviderWiringTest {

    @Nested
    @SpringBootTest(properties = {
            "spring.ai.model.chat=openai",
            "spring.ai.openai.api-key=test-key"
    })
    @ActiveProfiles("test")
    class WithOpenAi {

        @Autowired
        ChatModel chatModel;

        @Test
        void usesTheOpenAiChatModel() {
            assertThat(chatModel).isInstanceOf(OpenAiChatModel.class);
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "spring.ai.model.chat=anthropic",
            "spring.ai.anthropic.api-key=test-key",
            "spring.ai.openai.api-key="
    })
    @ActiveProfiles("test")
    class WithAnthropicAndNoOpenAiKey {

        @Autowired
        ChatModel chatModel;

        @Test
        void startsAndUsesTheAnthropicChatModel() {
            assertThat(chatModel).isInstanceOf(AnthropicChatModel.class);
        }
    }

    /**
     * The regression test. With no keys at all the context used to fail while creating an OpenAI
     * bean the run never asked for, so "switch with an env var" did not actually work.
     */
    @Nested
    @SpringBootTest(properties = {
            "spring.ai.model.chat=ollama",
            "spring.ai.openai.api-key=",
            "spring.ai.anthropic.api-key="
    })
    @ActiveProfiles("test")
    class WithOllamaAndNoOtherKeys {

        @Autowired
        ChatModel chatModel;

        @Test
        void startsAndUsesTheOllamaChatModel() {
            assertThat(chatModel).isInstanceOf(OllamaChatModel.class);
        }
    }
}
