# 02 - Streaming (LangChain4j)

**What:** the same answer twice - streamed token by token to the console, and streamed over HTTP
as server-sent events, using `StreamingChatResponseHandler` callbacks.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw -q compile exec:java

# in another terminal, while it is running
curl -N 'http://localhost:8080/chat/stream?q=explain+virtual+threads'
```

## Where to look

- [`Streaming.java`](src/main/java/com/example/cookbook/Streaming.java) - `onPartialResponse` / `onCompleteResponse` / `onError`, once for the console
  and once for the SSE endpoint. The HTTP server is the JDK built-in one, kept deliberately small.
- [`StreamingTest.java`](src/test/java/com/example/cookbook/StreamingTest.java) - a stub streaming model that emits a fixed token sequence.

## Gotchas

- The callbacks run on the HTTP client's thread, not on the caller's. The console example uses a
  `CountDownLatch` because `main` would otherwise exit before the first token arrives.
- Newlines have to be escaped in SSE payloads - a raw `\n` ends the event.
- `onError` is the only place a failure surfaces. There is no exception to catch around `chat(...)`.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/02-streaming) | [Previous: 01 Chat basics](../01-chat-basic) | [Next: 03 Structured output](../03-structured-output)
