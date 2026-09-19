package com.example.cookbook;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real contract of this recipe: a tool that lives in another process is advertised to the
 * model and actually executed there.
 *
 * A real MCP server runs in this JVM on a random port. Only the model is scripted - it asks for
 * a tool on the first turn, and the answer that comes back is the one the server produced.
 */
class McpEndToEndTest {

    private static TestMcpServer server;
    private static McpClient mcpClient;

    @BeforeAll
    static void startServerAndConnect() {
        server = TestMcpServer.started();
        mcpClient = new DefaultMcpClient.Builder()
                .transport(StreamableHttpMcpTransport.builder()
                        .url("http://localhost:" + server.port() + "/mcp")
                        .timeout(Duration.ofSeconds(20))
                        .build())
                .clientName("cookbook-test")
                .build();
    }

    @AfterAll
    static void disconnectAndStop() throws Exception {
        if (mcpClient != null) {
            mcpClient.close();
        }
        if (server != null) {
            server.close();
        }
    }

    /** Asks for the remote tool on the first turn, then answers. */
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
            return ChatResponse.builder().aiMessage(AiMessage.from("It has shipped.")).build();
        }
    }

    @Test
    void listsToolsPublishedByTheServer() {
        assertThat(mcpClient.listTools())
                .extracting(ToolSpecification::name)
                .containsExactlyInAnyOrder("orderStatus", "estimatedDelivery");
    }

    @Test
    void advertisesAndExecutesARemoteTool() {
        ScriptedModel model = new ScriptedModel();

        String answer = new McpAssistant(model, McpToolProvider.builder().mcpClients(mcpClient).build())
                .ask("Where is order A-1001?");

        assertThat(answer).isEqualTo("It has shipped.");

        // The tool reached the model without this project ever naming it ...
        assertThat(model.requests.get(0).toolSpecifications())
                .extracting(ToolSpecification::name)
                .contains("orderStatus");

        // ... and the result in the second request came back from the server process.
        assertThat(model.requests.get(1).messages().toString()).contains("shipped");
    }
}
