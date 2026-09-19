package com.example.cookbook;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provider switching is the promise on the front page of this repo, and it is the part most
 * likely to break silently: a starter auto-configures every model type it knows, and each one
 * wants a key at startup - so an unrelated bean can fail a run that never touches it.
 *
 * These tests boot the real application context with the keys a user would actually have, one
 * provider at a time, and assert that the wired model is the one that was asked for.
 * No model is called and no key is real.
 */
class ProviderWiringTest {

    @Nested
    @SpringBootTest(properties = {
            "spring.ai.model.embedding=openai",
            "spring.ai.openai.api-key=test-key"
    })
    @ActiveProfiles("test")
    class WithOpenAi {

        @Autowired
        EmbeddingModel embeddingModel;

        @Test
        void usesTheOpenAiEmbeddingModel() {
            assertThat(embeddingModel).isInstanceOf(OpenAiEmbeddingModel.class);
        }
    }

    /**
     * The regression test. With no OpenAI key at all, the context used to fail on
     * openAiEmbeddingModel even though the run asked for Ollama.
     */
    @Nested
    @SpringBootTest(properties = {
            "spring.ai.model.embedding=ollama",
            "spring.ai.openai.api-key="
    })
    @ActiveProfiles("test")
    class WithOllamaAndNoOpenAiKey {

        @Autowired
        EmbeddingModel embeddingModel;

        @Test
        void startsAndUsesTheOllamaEmbeddingModel() {
            assertThat(embeddingModel).isInstanceOf(OllamaEmbeddingModel.class);
        }
    }
}
