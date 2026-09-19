package com.example.cookbook;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Three guardrails at the two seams that exist: before the call and after it.
 *
 * An input guardrail that returns fatal() stops the call - nothing reaches the provider and
 * nothing is billed. successWith() rewrites the message instead of rejecting it, which is what
 * redaction needs.
 */
public final class Guards {

    private Guards() {
    }

    /** Blocks the request outright. No model call happens. */
    public static class PromptInjection implements InputGuardrail {

        private static final List<Pattern> PATTERNS = List.of(
                Pattern.compile("ignore (all|any|the) (previous|prior|above) instructions"),
                Pattern.compile("disregard (your|the) (system )?(prompt|instructions)"),
                Pattern.compile("reveal (your|the) (system )?prompt"),
                Pattern.compile("you are now (in )?(developer|dan|god) mode"));

        public PromptInjection() {
        }

        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            String text = userMessage.singleText().toLowerCase(Locale.ROOT);

            return PATTERNS.stream().anyMatch(pattern -> pattern.matcher(text).find())
                    ? fatal("input rejected: prompt injection")
                    : success();
        }
    }

    /** Rewrites the message instead of rejecting it. */
    public static class PiiRedaction implements InputGuardrail {

        public PiiRedaction() {
        }

        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            String text = userMessage.singleText();
            return Pii.contains(text) ? successWith(Pii.redact(text)) : success();
        }
    }

    /** Last line of defence: catches the answer that leaks what the request was not allowed to ask for. */
    public static class NoLeak implements OutputGuardrail {

        private final String systemPromptMarker;

        public NoLeak(String systemPromptMarker) {
            this.systemPromptMarker = systemPromptMarker.toLowerCase(Locale.ROOT);
        }

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            String text = responseFromLLM.text();
            if (text == null) {
                return success();
            }
            if (Pii.contains(text) || text.toLowerCase(Locale.ROOT).contains(systemPromptMarker)) {
                return fatal("output rejected: leaked content");
            }
            return success();
        }
    }
}
