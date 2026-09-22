# 19 - Retry and rate limits (Spring AI)

**What:** what to do when the provider says "not now". Spring AI's own retry layer, configured
instead of inherited, plus the thing it cannot express: an upper bound in wall-clock time on the
whole operation. Deliberately not here: queueing, token-bucket throttling or a circuit breaker -
those belong in front of the client, not inside one call.

## Run

```bash
export OPENAI_API_KEY=sk-...        # or: export OLLAMA_BASE_URL=http://localhost:11434
./mvnw spring-boot:run
```

With nothing listening you get the interesting path rather than an error:

```
> What does a 429 from an LLM provider usually mean?
gave up after 3 attempt(s) in PT5.34S: Connection refused
```

## Where to look

- `application.yml` - the `spring.ai.retry` block. Every value is set on purpose; see the first
  gotcha for what happens if you leave them out.
- `CallBudget.java` - `call()` checks the deadline *before* sleeping. A retry that cannot finish
  inside the budget is never started, which is the whole difference between "three attempts" and
  "at most eight seconds".
- `Transient.java` - the 429-or-503 versus 400-or-401 split. Retrying the second kind spends the
  budget and returns the same error.
- `RetryPropertiesTest.java` - asserts the yaml actually binds, and that the curve multiplies out
  to something a caller would wait for.

## Gotchas

- **The defaults are ten attempts, 2s initial, multiplier 5, capped at 3 minutes.** That is
  `2 + 10 + 50 + 180 x 6` seconds of waiting: a single call can sit there for **nineteen minutes**
  before it fails. Nothing warns you, because each individual number looks reasonable.
- Attempts and backoff are the only knobs, so the total wait is an emergent property nobody
  computes. If you take one thing from this recipe, take the habit of multiplying it out.
- `spring.ai.retry.on-client-errors` defaults to false, which is right - but it means a 429 is
  retried while a 400 is not, and both are 4xx. Check which bucket your provider puts overload in.
- Spring AI's backoff has **no jitter**. Every client that saw the same 429 retries at the same
  moment, and the provider gets the same spike one backoff later. `CallBudget` adds it; the
  framework layer underneath does not.
- Seeding a jitter generator with something stable per client - a shard index, a pod ordinal -
  defeats it. `new Random(n)` for sequential `n` returns nearly the same first value, so twenty
  clients land within a few milliseconds of each other. Measured, not assumed: see
  `CallBudgetTest.spreadsRetriesWithJitterSoClientsDoNotSynchronise`.
- A slow call spends the budget just as surely as a long sleep, so the deadline has to cover time
  spent inside the call too, not just the waits between attempts.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/19-retry) | [Previous: 18 Local models](../18-ollama)
