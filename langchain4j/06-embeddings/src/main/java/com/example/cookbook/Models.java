package com.example.cookbook;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

/**
 * Provider selection, done by hand so nothing is hidden.
 *
 * AI_PROVIDER wins if set; otherwise the first key that is present decides.
 * Every recipe in this repo carries its own copy - the projects are deliberately standalone.
 */
final class Models {

    private Models() {
    }

    static String provider() {
        String explicit = System.getenv("AI_PROVIDER");
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim().toLowerCase();
        }
        if (isSet("OPENAI_API_KEY")) {
            return "openai";
        }
        if (isSet("ANTHROPIC_API_KEY")) {
            return "anthropic";
        }
        return "ollama";
    }

    /** Anthropic has no embedding endpoint, so embedding recipes fall back to OpenAI or Ollama. */
    static EmbeddingModel embedding() {
        String provider = provider();
        if ("anthropic".equals(provider)) {
            provider = isSet("OPENAI_API_KEY") ? "openai" : "ollama";
        }
        return switch (provider) {
            case "openai" -> OpenAiEmbeddingModel.builder()
                    .apiKey(require("OPENAI_API_KEY"))
                    .modelName("text-embedding-3-small")
                    .build();
            case "ollama" -> OllamaEmbeddingModel.builder()
                    .baseUrl(envOr("OLLAMA_BASE_URL", "http://localhost:11434"))
                    .modelName("nomic-embed-text")
                    .build();
            default -> throw new IllegalStateException("Unknown AI_PROVIDER: " + provider);
        };
    }

    private static boolean isSet(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }

    static String require(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set. Copy .env.example and fill it in.");
        }
        return value;
    }

    static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
