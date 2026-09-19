# 04 - Tool calling (Spring AI)

**What:** give the model three Java methods and let it decide which to call. Spring AI runs the
whole request/tool/response loop; you only write the methods.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
./mvnw spring-boot:run -Dspring-boot.run.arguments="is A-1003 still coming?"
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

- [`OrderTools.java`](src/main/java/com/example/cookbook/OrderTools.java) - `@Tool` and `@ToolParam`. The descriptions are the prompt; treat them as
  such and write them for a reader who knows nothing about your domain.
- [`ToolCallingApplication.java`](src/main/java/com/example/cookbook/ToolCallingApplication.java) - `.tools(new OrderTools())`, one line.
- [`OrderToolsTest.java`](src/test/java/com/example/cookbook/OrderToolsTest.java) - asserts the generated schema and calls a tool with raw JSON arguments.

## Gotchas

- A vague description is the usual reason a tool never gets called. "Get status" loses to
  "Look up the delivery status of an order by its id, for example A-1001."
- Tools are executed on the calling thread with whatever arguments the model invented. Validate
  them - the model is not a trusted caller.
- Multi-step questions ("where is it and when will it arrive") cost several round trips, and each
  one resends the full history. Watch the token count before adding a fourth tool.
- `.call().content()` can return `null` if the model stops on a tool call it cannot resolve.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/04-tool-calling) | [Previous: 03 Structured output](../03-structured-output) | [Next: 05 Conversation memory](../05-memory)
