package com.example.cookbook;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real contract of this recipe: tools defined somewhere else reach the model, and the
 * application never names them.
 *
 * A real MCP server runs in this JVM on a random port, the Spring client connects to it during
 * startup exactly as it would to recipe 13, and the assertion is on what came back over the wire.
 * No model is called and no API key is needed - the chat model only has to be constructible.
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=not-used-by-this-test",
        "spring.ai.model.chat=openai"
})
@ActiveProfiles("test")
class McpEndToEndTest {

    // static, because @DynamicPropertySource runs before anything instance-level exists
    private static final TestMcpServer SERVER = TestMcpServer.started();

    @DynamicPropertySource
    static void pointTheClientAtTheTestServer(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.mcp.client.streamable-http.connections.orders.url",
                () -> "http://localhost:" + SERVER.port());
        registry.add("spring.ai.mcp.client.streamable-http.connections.orders.endpoint", () -> "/mcp");
    }

    @AfterAll
    static void stopServer() throws Exception {
        SERVER.close();
    }

    @Autowired
    McpAssistant assistant;

    @Test
    void discoversToolsFromAnotherProcess() {
        assertThat(assistant.toolNames()).hasSize(2);

        // Spring AI prefixes remote tool names with the connection name, so match on the suffix.
        assertThat(assistant.toolNames())
                .anySatisfy(name -> assertThat(name).endsWith("orderStatus"))
                .anySatisfy(name -> assertThat(name).endsWith("estimatedDelivery"));
    }

    @Test
    void theApplicationNeverNamedThoseTools() {
        // Nothing in src/main mentions them; they exist only because the server published them.
        assertThat(McpAssistant.class.getDeclaredFields())
                .noneSatisfy(field -> assertThat(field.getName()).containsIgnoringCase("order"));
    }
}
