# 04 - Tool calling (LangChain4j)

**What:** give the model three Java methods and let it decide which to call. `AiServices` runs the
request/tool/response loop.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw -q compile exec:java
./mvnw -q compile exec:java -Dexec.args="is A-1003 still coming?"
```

Known order ids: `A-1001` (shipped), `A-1002` (packing), `A-1003` (cancelled).

## Test

```bash
./mvnw test                                          # offline, no key, no network
COOKBOOK_LIVE=true AI_PROVIDER=ollama ./mvnw test    # also runs LiveModelTest
COOKBOOK_LIVE=true OPENAI_API_KEY=sk-... ./mvnw test
```

`LiveModelTest` is skipped unless `COOKBOOK_LIVE=true`. It checks the wiring against a real
provider - that a call succeeds, that usage metadata comes back, that the system prompt arrives -
and never asserts on what the model actually knows.

## Where to look

- [`OrderTools.java`](src/main/java/com/example/cookbook/OrderTools.java) - `@Tool` and `@P`. The descriptions are prompt text.
- [`ToolCalling.java`](src/main/java/com/example/cookbook/ToolCalling.java) - `.tools(new OrderTools())` on the `AiServices` builder.
- [`ToolCallingTest.java`](src/test/java/com/example/cookbook/ToolCallingTest.java) - a scripted model that asks for a tool on the first turn and answers on
  the second, so the whole loop is exercised offline.

## Gotchas

- **Compile with `-parameters`.** Tool argument names come from reflection. Without the flag the
  schema says `arg0`, the model sends `{"orderId": ...}`, and your method receives `null`. The pom
  sets `maven.compiler.parameters`, and that is not optional.
- A vague `@Tool` description is the usual reason a tool never gets called.
- Tools run on the calling thread with model-supplied arguments. Validate them.
- An exception thrown inside a tool is sent back to the model as text by default, not propagated.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/04-tool-calling) | [Previous: 03 Structured output](../03-structured-output) | [Next: 05 Conversation memory](../05-memory)
