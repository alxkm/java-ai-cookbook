package com.example.cookbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;

import java.util.Optional;
import java.util.function.Function;

/**
 * Structured output, made survivable on a small model.
 *
 * A hosted model asked for JSON returns JSON. A 3B model returns JSON about nine times out of ten,
 * and prose the tenth time - often JSON wrapped in a markdown fence, or with a sentence in front
 * of it. So: ask, and if the answer does not parse, salvage the first JSON object out of it before
 * spending another call.
 */
final class RetryingEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RetryingEntity() {
    }

    static <T> Optional<T> call(ChatModel model, String userText, Class<T> type, int attempts) {
        return call(model::chat, userText, type, attempts);
    }

    /** Takes a function rather than a model so the retry logic can be tested on its own. */
    static <T> Optional<T> call(Function<String, String> ask, String userText, Class<T> type, int attempts) {
        String prompt = userText + System.lineSeparator()
                + "Reply with a single JSON object and nothing else. Fields: " + String.join(", ", fieldNames(type));

        for (int attempt = 1; attempt <= attempts; attempt++) {
            Optional<T> parsed = parse(ask.apply(prompt), type);
            if (parsed.isPresent()) {
                return parsed;
            }
            System.out.println("  [attempt " + attempt + "/" + attempts + "] no usable JSON in the answer");
        }

        return Optional.empty();
    }

    private static String[] fieldNames(Class<?> type) {
        return type.isRecord()
                ? java.util.Arrays.stream(type.getRecordComponents()).map(c -> c.getName()).toArray(String[]::new)
                : new String[0];
    }

    private static <T> Optional<T> parse(String answer, Class<T> type) {
        return extractJsonObject(answer).flatMap(json -> {
            try {
                return Optional.ofNullable(MAPPER.readValue(json, type));
            } catch (Exception notJson) {
                return Optional.empty();
            }
        });
    }

    /**
     * Pull the first balanced JSON object out of whatever the model said. Models love to wrap JSON
     * in ```json fences or introduce it with "Sure! Here is the JSON:".
     */
    static Optional<String> extractJsonObject(String text) {
        if (text == null) {
            return Optional.empty();
        }

        int start = text.indexOf('{');
        if (start < 0) {
            return Optional.empty();
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            switch (c) {
                case '"' -> inString = true;
                case '{' -> depth++;
                case '}' -> {
                    depth--;
                    if (depth == 0) {
                        return Optional.of(text.substring(start, i + 1));
                    }
                }
                default -> {
                }
            }
        }

        return Optional.empty();
    }
}
