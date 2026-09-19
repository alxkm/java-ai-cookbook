package com.example.cookbook;

import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

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

    static StreamingChatModel streamingChat() {
        return switch (provider()) {
            case "openai" -> OpenAiStreamingChatModel.builder()
                    .apiKey(require("OPENAI_API_KEY"))
                    .modelName("gpt-4o-mini")
                    .temperature(0.2)
                    .build();
            case "anthropic" -> AnthropicStreamingChatModel.builder()
                    .apiKey(require("ANTHROPIC_API_KEY"))
                    .modelName("claude-sonnet-5")
                    .temperature(0.2)
                    .build();
            case "ollama" -> OllamaStreamingChatModel.builder()
                    .baseUrl(envOr("OLLAMA_BASE_URL", "http://localhost:11434"))
                    .modelName("llama3.2")
                    .temperature(0.2)
                    .build();
            default -> throw new IllegalStateException("Unknown AI_PROVIDER: " + provider());
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
