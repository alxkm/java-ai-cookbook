package com.example.cookbook;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The MCP protocol itself is covered by recipe 13, which runs a real client against a real server.
 * What is worth checking here is the part this recipe owns: tools that arrive from a ToolProvider
 * are advertised to the model and executed, without the application knowing their names.
 */
class McpAssistantTest {

    /** Stands in for McpToolProvider; from the assistant's side it is the same interface. */
    static class RemoteToolProvider implements ToolProvider {

        @Override
        public ToolProviderResult provideTools(dev.langchain4j.service.tool.ToolProviderRequest request) {
            ToolSpecification specification = ToolSpecification.builder()
                    .name("orderStatus")
                    .description("Look up the delivery status of an order by its id.")
                    .parameters(JsonObjectSchema.builder()
                            .addStringProperty("orderId", "Order id in the form A-nnnn")
                            .required("orderId")
                            .build())
                    .build();

            ToolExecutor executor = (ToolExecutionRequest toolRequest, Object memoryId) -> "shipped";

            return ToolProviderResult.builder()
                    .add(specification, executor)
                    .build();
        }
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
            return ChatResponse.builder().aiMessage(AiMessage.from("A-1001 has shipped.")).build();
        }
    }

    @Test
    void advertisesAndExecutesToolsItNeverDeclared() {
        ScriptedModel model = new ScriptedModel();

        String answer = new McpAssistant(model, new RemoteToolProvider()).ask("Where is A-1001?");

        assertThat(answer).isEqualTo("A-1001 has shipped.");
        assertThat(model.requests.get(0).toolSpecifications())
                .extracting(ToolSpecification::name)
                .containsExactly("orderStatus");
        assertThat(model.requests.get(1).messages().toString()).contains("shipped");
    }
}
