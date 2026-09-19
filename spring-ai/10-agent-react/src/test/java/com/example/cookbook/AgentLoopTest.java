package com.example.cookbook;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;

import java.util.List;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLoopTest {

    private static final String SYSTEM = "You are a support agent.";

    /** Replies are scripted by turn number, so a whole multi-step run is reproducible. */
    record ScriptedModel(IntFunction<ChatResponse> script) implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            return script.apply(turn(prompt));
        }

        private int turn(Prompt prompt) {
            return (int) prompt.getInstructions().stream()
                    .filter(message -> message instanceof AssistantMessage)
                    .count();
        }
    }

    private static ChatResponse toolCall(String id, String name, String arguments) {
        var call = new AssistantMessage.ToolCall(id, "function", name, arguments);
        return new ChatResponse(List.of(new Generation(
                AssistantMessage.builder().content("").toolCalls(List.of(call)).build())));
    }

    private static ChatResponse text(String answer) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
    }

    @Test
    void runsSeveralToolsThenAnswers() {
        ChatModel model = new ScriptedModel(turn -> switch (turn) {
            case 0 -> toolCall("1", "orderStatus", "{\"orderId\":\"A-1001\"}");
            case 1 -> toolCall("2", "estimatedDelivery", "{\"orderId\":\"A-1001\"}");
            default -> text("A-1001 has shipped and arrives in two days.");
        });

        AgentLoop.Outcome outcome = new AgentLoop(model, ToolCallingManager.builder().build(), 6, new OrderTools())
                .run(SYSTEM, "Where is A-1001?");

        assertThat(outcome.stoppedOnStepLimit()).isFalse();
        assertThat(outcome.answer()).contains("shipped");
        assertThat(outcome.steps()).extracting(AgentLoop.Step::toolName)
                .containsExactly("orderStatus", "estimatedDelivery");

        // The trace has to carry what the tool actually returned. Tool output lives in
        // ToolResponseMessage, not in the message text, and getting that wrong produces a
        // trace full of empty results that still looks like it works.
        // Values arrive JSON-encoded, which is exactly what the model is shown.
        assertThat(outcome.steps().get(0).result()).contains("shipped");
        assertThat(outcome.steps().get(1).result()).isNotBlank();
    }

    @Test
    void stopsAtTheStepLimitInsteadOfLoopingForever() {
        // A model that never stops asking for the same tool. This happens in practice.
        ChatModel model = new ScriptedModel(turn -> toolCall("1", "orderStatus", "{\"orderId\":\"A-1001\"}"));

        AgentLoop.Outcome outcome = new AgentLoop(model, ToolCallingManager.builder().build(), 3, new OrderTools())
                .run(SYSTEM, "Where is A-1001?");

        assertThat(outcome.stoppedOnStepLimit()).isTrue();
        assertThat(outcome.answer()).isNull();
        assertThat(outcome.steps()).hasSize(3);
    }
}
