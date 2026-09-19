package com.example.cookbook;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tool loop runs inside AiServices, so a scripted model is enough to exercise it end to end
 * without a key: first reply asks for a tool, second reply is the answer.
 */
class ToolCallingTest {

    interface SupportAgent {
        String answer(String question);
    }

    static class ScriptedModel implements ChatModel {
        final List<ChatRequest> requests = new ArrayList<>();

        @Override
        public ChatResponse chat(ChatRequest request) {
            requests.add(request);
            if (requests.size() == 1) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                                .id("call-1")
                                .name("orderStatus")
                                .arguments("{\"orderId\":\"A-1001\"}")
                                .build()))
                        .build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("Order A-1001 has shipped.")).build();
        }
    }

    @Test
    void executesToolAndFeedsResultBack() {
        ScriptedModel model = new ScriptedModel();

        String answer = AiServices.builder(SupportAgent.class)
                .chatModel(model)
                .tools(new OrderTools())
                .build()
                .answer("Where is order A-1001?");

        assertThat(answer).isEqualTo("Order A-1001 has shipped.");
        assertThat(model.requests).hasSize(2);

        // The tools were advertised on the first request ...
        assertThat(model.requests.get(0).toolSpecifications())
                .extracting(spec -> spec.name())
                .contains("orderStatus", "estimatedDelivery", "today");

        // ... and the tool output came back as a dedicated message on the second one.
        assertThat(model.requests.get(1).messages())
                .filteredOn(ToolExecutionResultMessage.class::isInstance)
                .singleElement()
                .satisfies(message ->
                        assertThat(((ToolExecutionResultMessage) message).text()).isEqualTo("shipped"));
    }
}
