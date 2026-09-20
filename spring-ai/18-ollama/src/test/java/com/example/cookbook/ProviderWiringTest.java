package com.example.cookbook;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
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
            // Deliberately a port nothing listens on. This test asserts wiring, so it never needs
            // a reachable Ollama - and pinning it to a dead address is what stops the recipe from
            // quietly depending on one again.
            "spring.ai.ollama.base-url=http://127.0.0.1:1",
            // Without this the autoconfiguration calls GET /api/tags while the chat model bean is
            // being built, to pull whatever is missing. Right for main(), fatal here: the context
            // died before a single assertion ran, so this passed on a machine with Ollama running
            // and failed on the CI runner.
            "spring.ai.ollama.init.pull-model-strategy=never"
    })
    @ActiveProfiles("test")
    class WithOllama {

        @Autowired
        ChatModel chatModel;

        @Test
        void usesTheOllamaChatModel() {
            assertThat(chatModel).isInstanceOf(OllamaChatModel.class);
        }
    }
}
