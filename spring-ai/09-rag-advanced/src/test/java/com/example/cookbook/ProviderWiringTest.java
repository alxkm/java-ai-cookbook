package com.example.cookbook;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provider switching is the promise on the front page of this repo, and it is the part most
 * likely to break silently: a starter auto-configures every model type it knows - embedding,
 * image, speech, transcription, moderation - and each one wants a key at startup. An unrelated
 * bean can fail a run that never touches it.
 *
 * The VectorStore is mocked away because the real bean embeds the whole handbook while the
 * context is still starting, and that is not what these tests are about.
 */
class ProviderWiringTest {

    @Nested
    @SpringBootTest(properties = {
            "spring.ai.model.chat=openai",
            "spring.ai.model.embedding=openai",
            "spring.ai.openai.api-key=test-key"
    })
    @ActiveProfiles("test")
    class WithOpenAi {

        @MockitoBean
        VectorStore vectorStore;

        @Autowired
        ChatModel chatModel;

        @Autowired
        EmbeddingModel embeddingModel;

        @Test
        void usesTheOpenAiModels() {
            assertThat(chatModel).isInstanceOf(OpenAiChatModel.class);
            assertThat(embeddingModel).isInstanceOf(OpenAiEmbeddingModel.class);
        }
    }

    /**
     * The regression test. With no OpenAI key the context used to fail while creating an OpenAI
     * bean the run never asked for, so "switch with an env var" did not actually work.
     */
    @Nested
    @SpringBootTest(properties = {
            "spring.ai.model.chat=ollama",
            "spring.ai.model.embedding=ollama",
            "spring.ai.openai.api-key="
    })
    @ActiveProfiles("test")
    class WithOllamaAndNoOpenAiKey {

        @MockitoBean
        VectorStore vectorStore;

        @Autowired
        ChatModel chatModel;

        @Autowired
        EmbeddingModel embeddingModel;

        @Test
        void startsAndUsesTheOllamaModels() {
            assertThat(chatModel).isInstanceOf(OllamaChatModel.class);
            assertThat(embeddingModel).isInstanceOf(OllamaEmbeddingModel.class);
        }
    }
}
