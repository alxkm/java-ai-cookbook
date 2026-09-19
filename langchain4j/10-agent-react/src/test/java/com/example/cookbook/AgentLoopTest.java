package com.example.cookbook;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLoopTest {

    private static final String SYSTEM = "You are a support agent.";

    /** Replies are scripted by turn number, so a whole multi-step run is reproducible. */
    record ScriptedModel(IntFunction<ChatResponse> script) implements ChatModel {

        @Override
        public ChatResponse chat(ChatRequest request) {
            long assistantTurns = request.messages().stream()
                    .filter(message -> message instanceof AiMessage)
                    .count();
            return script.apply((int) assistantTurns);
        }
    }

    private static ChatResponse toolCall(String name, String arguments) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                        .id("call-" + name)
                        .name(name)
                        .arguments(arguments)
                        .build()))
                .build();
    }

    private static ChatResponse text(String answer) {
        return ChatResponse.builder().aiMessage(AiMessage.from(answer)).build();
    }

    @Test
    void runsSeveralToolsThenAnswers() {
        ChatModel model = new ScriptedModel(turn -> switch (turn) {
            case 0 -> toolCall("orderStatus", "{\"orderId\":\"A-1001\"}");
            case 1 -> toolCall("estimatedDelivery", "{\"orderId\":\"A-1001\"}");
            default -> text("A-1001 has shipped and arrives in two days.");
        });

        AgentLoop.Outcome outcome = new AgentLoop(model, 6, new OrderTools()).run(SYSTEM, "Where is A-1001?");

        assertThat(outcome.stoppedOnStepLimit()).isFalse();
        assertThat(outcome.answer()).contains("shipped");
        assertThat(outcome.steps()).extracting(AgentLoop.Step::toolName)
                .containsExactly("orderStatus", "estimatedDelivery");
        assertThat(outcome.steps().get(0).result()).isEqualTo("shipped");
    }

    @Test
    void stopsAtTheStepLimitInsteadOfLoopingForever() {
        // A model that never stops asking for the same tool. This happens in practice.
        ChatModel model = new ScriptedModel(turn -> toolCall("orderStatus", "{\"orderId\":\"A-1001\"}"));

        AgentLoop.Outcome outcome = new AgentLoop(model, 3, new OrderTools()).run(SYSTEM, "Where is A-1001?");

        assertThat(outcome.stoppedOnStepLimit()).isTrue();
        assertThat(outcome.answer()).isNull();
        assertThat(outcome.steps()).hasSize(3);
    }
}
