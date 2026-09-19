# 16 - Observability (Spring AI)

**What:** two layers. Spring AI's own observations (what a tracing backend picks up) and a token
and cost advisor (what finance asks about).

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

Three questions run, then the meters are printed: `gen_ai.client.operation` from the framework,
`cookbook.chat.*` from the advisor.

## Where to look

- [`TokenUsageAdvisor.java`](src/main/java/com/example/cookbook/TokenUsageAdvisor.java) - tokens, latency and USD cost, tagged by model. Prices are in the code
  on purpose: they change, and only you know your contract.
- [`application.yml`](src/main/resources/application.yml) - `spring.ai.chat.observations.log-prompt` is off by default, and that default
  is correct.
- [`TokenUsageAdvisorTest.java`](src/test/java/com/example/cookbook/TokenUsageAdvisorTest.java) - a model that reports usage like a real provider, so the metrics
  path runs without an API key.

## Gotchas

- `log-prompt` / `log-completion` put user data in your logs. Turn them on deliberately, for a
  debugging session, and know where those logs end up.
- Streaming responses report usage only in the final chunk, and some providers omit it. Cost
  tracking on a streamed call needs care.
- Tag by model, not by prompt. A tag with unbounded cardinality will take your metrics backend down.
- Tokens are not the whole bill. Cached input, batch discounts and image inputs are priced
  differently - this advisor is a good estimate, not an invoice.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/16-observability) | [Previous: 15 Guardrails](../15-guardrails) | [Next: 17 Evals](../17-evals)
