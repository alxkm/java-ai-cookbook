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

- `application.yml` - every retry layer underneath the budget switched off, one per kind of
  client, with each provider's per-attempt timeout capped at the budget's deadline.
- `CallBudget.java` - `call()` checks the deadline *before* sleeping. A retry that cannot finish
  inside the budget is never started, which is the whole difference between "three attempts" and
  "at most eight seconds".
- `Transient.java` - the 429-or-503 versus 400-or-401 split, in all three dialects Spring AI 2.0
  speaks. Retrying the second kind spends the budget and returns the same error.
- `TransientTest.java` - the real exceptions the OpenAI and Anthropic SDKs throw, and the budget
  retrying a real OpenAI 429.
- `RetryPropertiesTest.java` - asserts every layer underneath is off, and that no single attempt
  can outlast the deadline.

## Gotchas

- **In Spring AI 2.0, `spring.ai.retry` does not reach OpenAI or Anthropic.** Those models are
  built on the providers' own SDKs, which retry by themselves - `spring.ai.openai.chat.max-retries`,
  three by default on a 60-second timeout - and throw their own exceptions untouched. Only the
  models still on Spring AI's RetryTemplate, Ollama here, read `spring.ai.retry`. Configure the
  block for OpenAI and nothing happens: the properties bind, a test that checks them passes, and
  the SDK goes on retrying its own way.
- **`max-attempts` counts retries.** It is handed to `RetryPolicy.maxRetries`, so the default of
  ten is eleven attempts, and with a 2s start, a multiplier of 5 and a 3-minute ceiling that is
  `2 + 10 + 50 + 180 x 7` seconds - **about twenty-two minutes** on one call before it fails.
  `0` means one attempt. Asserted in `RetryPropertiesTest`, both ways.
- **Pick one layer to own retrying.** Every one of the three retries by default, so a budget on
  top multiplies rather than bounds: three budget attempts over an SDK that tries four times is
  twelve calls. Here the budget owns it and every layer underneath is set to zero.
- A classifier that knows only Spring's exception types sees an OpenAI 429 - a
  `com.openai.errors.RateLimitException` - as permanent, and gives up on exactly the error
  retrying exists for. It did, in this recipe, until `TransientTest` used the SDK's real types.
- Attempts and backoff are the only knobs, so the total wait is an emergent property nobody
  computes. If you take one thing from this recipe, take the habit of multiplying it out.
- Spring AI's RetryTemplate backoff has **no jitter**. Every client that saw the same 429 retries
  at the same moment, and the provider gets the same spike one backoff later. `CallBudget` adds it.
- Seeding a jitter generator with something stable per client - a shard index, a pod ordinal -
  defeats it. `new Random(n)` for sequential `n` returns nearly the same first value, so twenty
  clients land within a few milliseconds of each other. Measured, not assumed: see
  `CallBudgetTest.spreadsRetriesWithJitterSoClientsDoNotSynchronise`.
- A slow call spends the budget just as surely as a long sleep, so the deadline has to cover time
  spent inside the call too. The budget can only check between attempts, which is why each
  provider's own timeout is capped at the deadline: the SDK default of 60 seconds would let the
  first attempt alone run seven times over an eight-second budget.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/19-retry) | [Previous: 18 Local models](../18-ollama) | [Next: 20 Progressive tool disclosure](../20-tool-search)
