# 18 - Local models (LangChain4j)

**What:** chat, structured output and embeddings with no API key and no network beyond localhost.
Also what breaks when you drop from a hosted model to a 3B one, and what to do about it.

## Run

```bash
# once
ollama serve
ollama pull llama3.2
ollama pull nomic-embed-text

./mvnw -q compile exec:java
./mvnw -q compile exec:java -Dexec.args="what is a sealed interface"

# a bigger model, if you have the RAM
OLLAMA_CHAT_MODEL=llama3.1:8b ./mvnw -q compile exec:java
```

## Where to look

- [`LocalModels.java`](src/main/java/com/example/cookbook/LocalModels.java) - `numCtx` and a generous timeout. Both matter more locally than they do
  against a hosted provider.
- [`RetryingEntity.java`](src/main/java/com/example/cookbook/RetryingEntity.java) - retry plus salvage the first JSON object out of a chatty answer. This is
  the code you end up writing for local structured output.
- [`RetryingEntityTest.java`](src/test/java/com/example/cookbook/RetryingEntityTest.java) - seven cases, including braces inside strings. The retry logic takes a
  `Function<String, String>` so it can be tested without a model at all.

## Gotchas

- Set `numCtx` explicitly. The default is small and Ollama truncates from the front, silently -
  your system prompt is what disappears first.
- Structured output is the first thing to degrade. `AiServices` with a record return type works
  against OpenAI and fails intermittently against a 3B model; that is why this recipe parses by hand.
- Tool calling degrades next, and not every Ollama model supports it at all.
- The first call after a cold start loads the model into memory and can take a minute. That is not
  a hang; the timeout here is five minutes for that reason.
- A 3B model is fine for classification and routing, and poor at multi-step reasoning. Pick the
  recipe you use it for.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/18-ollama) | [Previous: 17 Evals](../17-evals)
