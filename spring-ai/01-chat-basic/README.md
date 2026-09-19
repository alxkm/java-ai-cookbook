# 01 - Chat basics (Spring AI)

**What:** the smallest useful Spring AI app. One `ChatClient`, one system prompt, one question.
Shows how provider selection is a configuration concern, not a code concern.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run

# or pass your own question
./mvnw spring-boot:run -Dspring-boot.run.arguments="explain virtual threads"

# other providers
AI_PROVIDER=anthropic ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run
AI_PROVIDER=ollama ./mvnw spring-boot:run
```

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

- [`ChatBasicApplication.java`](src/main/java/com/example/cookbook/ChatBasicApplication.java) - the `ChatClient` bean with a default system prompt, and a
  `CommandLineRunner` that sends one user message.
- [`application.yml`](src/main/resources/application.yml) - three providers configured side by side; `spring.ai.model.chat` picks one.
- [`ChatBasicTest.java`](src/test/java/com/example/cookbook/ChatBasicTest.java) - a stub `ChatModel` proves the prompt is assembled correctly without a key.

## Gotchas

- All three model starters are on the classpath on purpose. Without `spring.ai.model.chat`,
  auto-configuration would create several `ChatModel` beans and the context would fail.
- `spring.ai.model.chat` only switches **chat**. The OpenAI starter also auto-configures embedding,
  image, speech, transcription and moderation models, and each of those wants an OpenAI key at
  startup. Run with `AI_PROVIDER=ollama` and no OpenAI key and the context fails on
  `openAiEmbeddingModel` - nothing to do with chat. That is why `application.yml` here sets the
  unused model types to `none`.
- `ChatClient` is stateless. Nothing is remembered between calls - see recipe 05 for memory.
- `.call().content()` returns `null` when the model replies with tool calls only. Recipe 04 covers that.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/01-chat-basic) | [Next: 02 Streaming](../02-streaming)
