# 19 - Retry and rate limits (LangChain4j)

**What:** what to do when the provider says "not now". The model's own `maxRetries`, the exception
hierarchy that tells you what is worth repeating, and a wall-clock budget around the whole
operation. Deliberately not here: queueing, token-bucket throttling or a circuit breaker - those
belong in front of the client, not inside one call.

## Run

```bash
export OPENAI_API_KEY=sk-...        # or: export OLLAMA_BASE_URL=http://localhost:11434
./mvnw -q compile exec:java
```

With nothing listening you get the interesting path rather than an error:

```
> What does a 429 from an LLM provider usually mean?
gave up after 3 attempt(s) in PT5.34S: java.net.ConnectException
```

## Where to look

- `Models.java` - `maxRetries` and `timeout` are the entire retry surface a model builder exposes.
  The curve underneath is `RetryUtils`, and you cannot reach it from there.
- `Transient.java` - two `instanceof` checks and nothing else, because LangChain4j has already
  sorted its exceptions: `RateLimitException`, `TimeoutException` and `InternalServerException`
  extend `RetriableException`; `AuthenticationException` and `InvalidRequestException` extend
  `NonRetriableException`.
- `CallBudget.java` - `call()` checks the deadline *before* sleeping, so a retry that cannot finish
  inside the budget is never started.

## Gotchas

- `maxRetries` is a count, not a bound. The model retries with a 1s base, a 1.5 exponent and 0.2
  jitter, none of which a model builder lets you change - so the only way to cap the total time is
  to wrap the call, which is what `CallBudget` does.
- The model retries *inside* your retry. Two layers of three attempts is nine calls, and a nine-way
  multiplication is rarely what anyone meant. Pick which layer owns the retrying.
- `ContentFilteredException` extends `InvalidRequestException`, so a content-policy refusal is
  correctly never retried - even though it arrives looking like a server-side rejection.
- A refused connection and a socket timeout are raised by the JDK client before any provider
  mapping happens, so they land in neither branch of the hierarchy. They have to be classified by
  hand, and both are worth repeating: that is what a rolling restart looks like from the client.
- Seeding a jitter generator with something stable per client - a shard index, a pod ordinal -
  defeats it. `new Random(n)` for sequential `n` returns nearly the same first value, so twenty
  clients land within a few milliseconds of each other.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/19-retry) | [Previous: 18 Local models](../18-ollama)
