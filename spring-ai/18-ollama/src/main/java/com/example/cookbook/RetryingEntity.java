package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;

import java.util.Optional;

/**
 * Structured output, made survivable on a small model.
 *
 * A hosted model asked for JSON returns JSON. A 3B model returns JSON about nine times out of ten,
 * and prose the tenth time - often JSON wrapped in a markdown fence, or with a sentence in front
 * of it. So: ask, and if the answer does not parse, salvage the first JSON object out of it before
 * spending another call.
 */
final class RetryingEntity {

    private RetryingEntity() {
    }

    static <T> Optional<T> call(ChatClient chatClient, String userText, Class<T> type, int attempts) {
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(type);
        String prompt = userText + System.lineSeparator() + converter.getFormat();

        for (int attempt = 1; attempt <= attempts; attempt++) {
            String answer = chatClient.prompt().user(prompt).call().content();

            Optional<T> parsed = parse(converter, answer);
            if (parsed.isPresent()) {
                return parsed;
            }

            System.out.println("  [attempt " + attempt + "/" + attempts + "] no usable JSON in the answer");
        }

        return Optional.empty();
    }

    private static <T> Optional<T> parse(BeanOutputConverter<T> converter, String answer) {
        if (answer == null) {
            return Optional.empty();
        }
        return extractJsonObject(answer).flatMap(json -> {
            try {
                return Optional.ofNullable(converter.convert(json));
            } catch (RuntimeException notJson) {
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
