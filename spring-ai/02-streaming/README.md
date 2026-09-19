# 02 - Streaming (Spring AI)

**What:** the same answer twice - streamed token by token to the console, and streamed over HTTP
as server-sent events. The `Flux<String>` returned by `ChatClient` is the whole mechanism.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run

# in another terminal, while it is running
curl -N 'http://localhost:8080/chat/stream?q=explain+virtual+threads'
```

## Where to look

- [`StreamingApplication.java`](src/main/java/com/example/cookbook/StreamingApplication.java) - `.stream().content()` instead of `.call().content()`.
- [`StreamingController.java`](src/main/java/com/example/cookbook/StreamingController.java) - the only thing that makes it SSE is `produces = text/event-stream`.

## Gotchas

- `blockLast()` in the runner is there because a `CommandLineRunner` is not reactive. Do not
  block inside a WebFlux request thread - the controller returns the `Flux` and lets the server drain it.
- Streaming responses carry token usage only in the final chunk, and some providers omit it entirely.
- If the client disconnects, the `Flux` is cancelled but the provider may already have been billed
  for the full completion.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/02-streaming) | [Previous: 01 Chat basics](../01-chat-basic) | [Next: 03 Structured output](../03-structured-output)
