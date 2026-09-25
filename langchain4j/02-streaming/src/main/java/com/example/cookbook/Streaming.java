package com.example.cookbook;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Two ways to consume a token stream: straight to the console, and over HTTP as
 * server-sent events. Both use the same handler callbacks.
 */
public class Streaming {

    public static void main(String[] args) throws Exception {
        StreamingChatModel model = Models.streamingChat();

        streamToConsole(model, "Explain Java virtual threads to a backend engineer.");
        serveSse(model, 8080);
    }

    private static void streamToConsole(StreamingChatModel model, String question) throws InterruptedException {
        System.out.println("> " + question);
        CountDownLatch done = new CountDownLatch(1);

        model.chat(question, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
                System.out.print(token);
                System.out.flush();
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                // tokenUsage() is null for providers that do not report it on a stream, and
                // printing it unguarded turns a finished answer into a stack trace.
                System.out.printf("%n%n[done, %s tokens]%n", response.tokenUsage() == null
                        ? "unreported" : response.tokenUsage().totalTokenCount());
                done.countDown();
            }

            @Override
            public void onError(Throwable error) {
                error.printStackTrace();
                done.countDown();
            }
        });

        // The handler runs on the HTTP client's thread, so main has to wait for it.
        done.await();
    }

    /** Returns the server so a test can start it on port 0 and stop it again. */
    static HttpServer serveSse(StreamingChatModel model, int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/chat/stream", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String question = query != null && query.startsWith("q=")
                    ? URLDecoder.decode(query.substring(2), StandardCharsets.UTF_8)
                    : "Say hello.";

            exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, 0);

            OutputStream out = exchange.getResponseBody();
            CountDownLatch done = new CountDownLatch(1);

            model.chat(List.<ChatMessage>of(UserMessage.from(question)), new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String token) {
                    write(out, Sse.data(token));
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    write(out, Sse.event("done", ""));
                    done.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    // getMessage() can be null and can contain newlines; both would break the frame.
                    write(out, Sse.event("error", String.valueOf(error.getMessage())));
                    done.countDown();
                }
            });

            try {
                done.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("SSE endpoint: curl -N 'http://localhost:" + server.getAddress().getPort()
                + "/chat/stream?q=hello'");
        System.out.println("Ctrl+C to stop.");
        return server;
    }

    private static void write(OutputStream out, String chunk) {
        try {
            out.write(chunk.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOExceptionWrapper(e);
        }
    }

    private static class UncheckedIOExceptionWrapper extends RuntimeException {
        UncheckedIOExceptionWrapper(IOException cause) {
            super(cause);
        }
    }
}
