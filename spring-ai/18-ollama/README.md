# 18 - Local models (Spring AI)

**What:** chat, structured output and embeddings with no API key and no network beyond localhost.
Also what breaks when you drop from a hosted model to a 3B one, and what to do about it.

## Run

```bash
# once
ollama serve

./mvnw spring-boot:run
./mvnw spring-boot:run -Dspring-boot.run.arguments="what is a sealed interface"

# a bigger model, if you have the RAM
OLLAMA_CHAT_MODEL=llama3.1:8b ./mvnw spring-boot:run
```

The models are pulled on first start (`pull-model-strategy: when_missing`), which takes a few
minutes. Nothing leaves your machine.

## Where to look

- [`application.yml`](src/main/resources/application.yml) - `num-ctx` and the pull strategy. Both matter more locally than they do
  against a hosted provider.
- [`RetryingEntity.java`](src/main/java/com/example/cookbook/RetryingEntity.java) - retry plus salvage the first JSON object out of a chatty answer. This is
  the code you end up writing for local structured output.
- [`RetryingEntityTest.java`](src/test/java/com/example/cookbook/RetryingEntityTest.java) - seven cases, including braces inside strings.

## Gotchas

- Set `num-ctx` explicitly. The default is small and Ollama truncates from the front, silently -
  your system prompt is what disappears first.
- Structured output is the first thing to degrade. A small model will hand you JSON in a markdown
  fence, or JSON with an apology in front of it.
- Tool calling degrades next, and not every Ollama model supports it at all.
- The first call after a cold start loads the model into memory and can take a minute. That is not
  a hang; budget for it in timeouts.
- Quality per parameter is not the whole story - a 3B model is fine for classification and routing,
  and poor at multi-step reasoning. Pick the recipe you use it for.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/18-ollama) | [Previous: 17 Evals](../17-evals) | [Next: 19 Retry and rate limits](../19-retry)
