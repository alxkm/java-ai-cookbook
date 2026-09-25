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
- [`Sse.java`](src/main/java/com/example/cookbook/Sse.java) - the event framing, which is the part that is easy to get subtly wrong.
- [`StreamingEndToEndTest.java`](src/test/java/com/example/cookbook/StreamingEndToEndTest.java) - runs the real endpoint on a free port and reads it back
  the way a browser does, newlines and leading spaces included.

## Gotchas

- The callbacks run on the HTTP client's thread, not on the caller's. The console example uses a
  `CountDownLatch` because `main` would otherwise exit before the first token arrives.
- **A data field cannot contain a line break, and escaping is not the fix.** The spec wants one
  `data:` line per line of the payload, which the client joins back with `\n`. Write the break
  into a single field and the client ends the field there - everything after the first newline
  in a token is dropped silently, and model output is full of newlines. This recipe had exactly
  that bug until `StreamingEndToEndTest` called the endpoint instead of the stub.
- The spec strips one leading space after `data:`, so the writer must emit `data: ` with the space.
  Tokens usually start with one - `" threads"` - and it has to survive the round trip.
- Spring's side of this recipe never has the problem: WebFlux encodes a `Flux<String>` as
  `text/event-stream` and splits lines itself. Hand-rolling SSE means owning the framing.
- `onError` is the only place a failure surfaces. There is no exception to catch around `chat(...)`.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/02-streaming) | [Previous: 01 Chat basics](../01-chat-basic) | [Next: 03 Structured output](../03-structured-output)
