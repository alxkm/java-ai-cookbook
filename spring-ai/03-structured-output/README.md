# 03 - Structured output (Spring AI)

**What:** get a Java record back instead of a string. `entity()` generates a JSON schema from the
type, appends it to the prompt, and parses the reply.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
./mvnw spring-boot:run -Dspring-boot.run.arguments="beef stroganoff"
```

## Where to look

- [`StructuredOutputApplication.java`](src/main/java/com/example/cookbook/StructuredOutputApplication.java) - `entity(Recipe.class)` for a single object and
  `entity(new ParameterizedTypeReference<List<Recipe>>() {})` for a collection.
- [`StructuredOutputTest.java`](src/test/java/com/example/cookbook/StructuredOutputTest.java) - asserts both the parsing and the fact that the schema really is
  sent to the model.

## Gotchas

- The type reference is not optional for collections. `entity(List.class)` compiles and then fails
  at runtime with a useless error, because the element type is erased.
- Schema instructions are prompt text, not a hard guarantee. A small local model will still hand you
  prose sometimes - wrap the call in a retry, or turn on the provider's native structured output
  (`spring.ai.openai.chat.response-format`).
- Field names in the record are the field names in the schema. Renaming a record component changes
  the prompt.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/03-structured-output) | [Previous: 02 Streaming](../02-streaming) | [Next: 04 Tool calling](../04-tool-calling)
