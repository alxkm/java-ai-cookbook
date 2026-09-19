package com.example.cookbook;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;

import java.time.Duration;

/**
 * Ollama only. No key, no provider switch, no network beyond localhost.
 */
final class LocalModels {

    private LocalModels() {
    }

    static String baseUrl() {
        return envOr("OLLAMA_BASE_URL", "http://localhost:11434");
    }

    static ChatModel chat() {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl())
                .modelName(envOr("OLLAMA_CHAT_MODEL", "llama3.2"))
                .temperature(0.2)
                // Local models default to a small context. Set it explicitly or long prompts are
                // silently truncated from the front.
                .numCtx(8192)
                // First call after a cold start loads the model into memory, which is slow.
                .timeout(Duration.ofMinutes(5))
                .build();
    }

    static EmbeddingModel embedding() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl())
                .modelName(envOr("OLLAMA_EMBEDDING_MODEL", "nomic-embed-text"))
                .timeout(Duration.ofMinutes(5))
                .build();
    }

    static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
