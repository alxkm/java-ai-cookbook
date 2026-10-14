package com.example.cookbook;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The recipe's real models against a local server that is always overloaded, counting requests.
 *
 * CallBudgetTest proves the budget with a lambda; it cannot see that the model underneath retries
 * too. With the models left at maxRetries(2), three budget attempts here were nine requests.
 */
class ModelRetriesTest {

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"error\":{\"message\":\"overloaded\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(503, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static CallBudget budget() {
        return CallBudget.builder()
                .maxAttempts(3)
                .deadline(Duration.ofSeconds(30))
                .jitter(0)
                .random(new Random(1))
                .sleeper(duration -> { })
                .retryable(Transient.classifier())
                .build();
    }

    private void assertOneRequestPerAttempt(ChatModel model) {
        assertThatThrownBy(() -> budget().call(() -> model.chat("hello")))
                .isInstanceOf(CallBudget.BudgetExhaustedException.class)
                .satisfies(e -> assertThat(((CallBudget.BudgetExhaustedException) e).attempts()).isEqualTo(3));

        assertThat(requests).hasValue(3);
    }

    @Test
    void theOpenAiModelLeavesRetryingToTheBudget() {
        assertOneRequestPerAttempt(Models.openAi("test-key", baseUrl() + "/v1"));
    }

    @Test
    void theOllamaModelLeavesRetryingToTheBudget() {
        assertOneRequestPerAttempt(Models.ollama(baseUrl()));
    }

    @Test
    void noSingleRequestCanOutlastTheBudget() {
        // The budget checks the clock between attempts, so the per-request timeout is what bounds
        // the first call. The LangChain4j default is sixty seconds.
        assertThat(Models.MODEL_TIMEOUT).isLessThanOrEqualTo(Retry.DEADLINE);
    }
}
