# 16 - Observability (LangChain4j)

**What:** a `ChatModelListener` that records tokens, latency, errors and USD cost into Micrometer.
It is the hook every LangChain4j model implementation calls.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw -q compile exec:java
```

Three questions run, then the meters are printed.

## Where to look

- [`MetricsListener.java`](src/main/java/com/example/cookbook/MetricsListener.java) - `onRequest` / `onResponse` / `onError`. Note `context.attributes()`:
  that is the per-call map that carries the timer sample between the callbacks.
- [`MetricsListenerTest.java`](src/test/java/com/example/cookbook/MetricsListenerTest.java) - the contexts have public constructors, so the whole measurement path
  runs offline without a provider.

## Gotchas

- Do not keep per-call state in a listener field. One listener instance serves every concurrent
  call; `attributes()` exists for exactly this.
- Listeners are configured on the model, not on the call. That is what makes calls buried inside an
  `AiService` visible too.
- Streaming responses report usage only at the end, and some providers omit it.
- Tag by model, not by prompt. A tag with unbounded cardinality will take your metrics backend down.
- Tokens are not the whole bill. Cached input, batch discounts and image inputs are priced
  differently - this is a good estimate, not an invoice.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/16-observability) | [Previous: 15 Guardrails](../15-guardrails) | [Next: 17 Evals](../17-evals)
