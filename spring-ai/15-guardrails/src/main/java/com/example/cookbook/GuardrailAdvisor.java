package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One advisor doing three jobs, because they all live at the same seam: before the call, and after it.
 *
 *   in  - refuse obvious prompt injection, redact PII out of the user message
 *   out - refuse to return an answer that leaked PII or the system prompt
 *
 * An advisor that blocks simply never calls the chain. Nothing reaches the provider, and nothing
 * is billed.
 */
class GuardrailAdvisor implements CallAdvisor {

    private static final List<Pattern> INJECTION = List.of(
            Pattern.compile("ignore (all|any|the) (previous|prior|above) instructions"),
            Pattern.compile("disregard (your|the) (system )?(prompt|instructions)"),
            Pattern.compile("reveal (your|the) (system )?prompt"),
            Pattern.compile("you are now (in )?(developer|dan|god) mode"));

    static final String REFUSAL = "I cannot help with that request.";

    private final String systemPromptMarker;

    GuardrailAdvisor(String systemPromptMarker) {
        this.systemPromptMarker = systemPromptMarker.toLowerCase(Locale.ROOT);
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String userText = lastUserText(request);

        if (looksLikeInjection(userText)) {
            return refuse(request, "input rejected: prompt injection");
        }

        ChatClientRequest redacted = Pii.contains(userText)
                ? withUserText(request, Pii.redact(userText))
                : request;

        ChatClientResponse response = chain.nextCall(redacted);

        String answer = response.chatResponse() == null
                ? null
                : response.chatResponse().getResult().getOutput().getText();

        if (answer != null && (Pii.contains(answer) || answer.toLowerCase(Locale.ROOT).contains(systemPromptMarker))) {
            return refuse(request, "output rejected: leaked content");
        }

        return response;
    }

    @Override
    public String getName() {
        return "guardrails";
    }

    @Override
    public int getOrder() {
        // Runs first on the way in, last on the way out.
        return Integer.MIN_VALUE + 100;
    }

    private boolean looksLikeInjection(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return INJECTION.stream().anyMatch(pattern -> pattern.matcher(lower).find());
    }

    private String lastUserText(ChatClientRequest request) {
        List<Message> messages = request.prompt().getInstructions();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMessage) {
                return userMessage.getText();
            }
        }
        return null;
    }

    private ChatClientRequest withUserText(ChatClientRequest request, String text) {
        List<Message> messages = request.prompt().getInstructions().stream()
                .map(message -> message instanceof UserMessage ? (Message) new UserMessage(text) : message)
                .toList();

        return request.mutate()
                .prompt(new Prompt(messages, request.prompt().getOptions()))
                .build();
    }

    private ChatClientResponse refuse(ChatClientRequest request, String reason) {
        System.out.println("  [guardrail] " + reason);

        return ChatClientResponse.builder()
                .chatResponse(new ChatResponse(List.of(new Generation(new AssistantMessage(REFUSAL)))))
                .context(request.context())
                .build();
    }
}
