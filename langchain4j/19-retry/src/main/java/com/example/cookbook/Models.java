package com.example.cookbook;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;

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

    /**
     * maxRetries and timeout are the whole retry surface a LangChain4j model exposes. The curve
     * underneath - 1s base, a 1.5 exponent and 0.2 jitter - lives in RetryUtils and is not
     * reachable from a model builder, so a wall-clock budget has to sit outside the model.
     *
     * Once it does, the model's own retrying has to go. Left at two, every attempt the budget makes
     * is three requests, and a model timeout longer than the deadline lets the first attempt alone
     * outlast it: the budget checks the clock between attempts and cannot stop one that is running.
     */
    static final int MODEL_RETRIES = 0;

    static final Duration MODEL_TIMEOUT = Retry.DEADLINE;

    static ChatModel chat() {
        return switch (provider()) {
            case "openai" -> openAi(require("OPENAI_API_KEY"), "https://api.openai.com/v1");
            case "anthropic" -> AnthropicChatModel.builder()
                    .apiKey(require("ANTHROPIC_API_KEY"))
                    .maxRetries(MODEL_RETRIES)
                    .timeout(MODEL_TIMEOUT)
                    .modelName("claude-sonnet-5")
                    .temperature(0.2)
                    .build();
            case "ollama" -> ollama(envOr("OLLAMA_BASE_URL", "http://localhost:11434"));
            default -> throw new IllegalStateException("Unknown AI_PROVIDER: " + provider());
        };
    }

    static ChatModel openAi(String apiKey, String baseUrl) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .maxRetries(MODEL_RETRIES)
                .timeout(MODEL_TIMEOUT)
                .modelName("gpt-4o-mini")
                .temperature(0.2)
                .build();
    }

    static ChatModel ollama(String baseUrl) {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                // Ollama defaults to a small context and truncates from the front, silently.
                // A long prompt plus a long answer hits it and you get half a JSON object back.
                .numCtx(8192)
                .maxRetries(MODEL_RETRIES)
                .timeout(MODEL_TIMEOUT)
                .modelName("llama3.2")
                .temperature(0.2)
                .build();
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
