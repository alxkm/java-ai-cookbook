# 03 - Structured output (LangChain4j)

**What:** declare an interface, return a record, let `AiServices` deal with the schema and the
parsing. The prompt lives in the `@UserMessage` annotation.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw -q compile exec:java
./mvnw -q compile exec:java -Dexec.args="beef stroganoff"
```

## Where to look

- [`StructuredOutput.java`](src/main/java/com/example/cookbook/StructuredOutput.java) - the `Chef` interface; the return type is the whole contract.
- [`StructuredOutputTest.java`](src/test/java/com/example/cookbook/StructuredOutputTest.java) - the stub model returns JSON and the record comes out typed.

## Gotchas

- `{{dish}}` placeholders need `@V("dish")` on the parameter. Without it the template is sent
  verbatim and you get a confused answer instead of an error.
- When the model supports native JSON schema (OpenAI does), LangChain4j uses it; otherwise it falls
  back to putting the schema in the prompt. Same code, different reliability.
- **A list cannot be returned directly.** `List<Recipe>` works against a provider with native JSON
  schema support and throws `IllegalStateException` against one without it, because there is no
  prompt-only format instruction for a collection of objects. Wrap it in a record, as `Variants`
  does here.
- Parsing failures throw. Run this against Ollama and `variants()` fails most of the time: a 3B
  model returns `{"recipes":[...]` and drops the closing brace. The single-object call is fine.
  Recipe 18 ([Spring AI](../../spring-ai/18-ollama), [LangChain4j](../18-ollama)) shows the
  retry-and-salvage pattern that makes this survivable.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/03-structured-output) | [Previous: 02 Streaming](../02-streaming) | [Next: 04 Tool calling](../04-tool-calling)
