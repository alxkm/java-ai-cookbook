# 01 - Chat basics (LangChain4j)

**What:** the smallest useful LangChain4j app. Build a `ChatModel`, send a system and a user
message, print the answer and the token usage.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw -q compile exec:java

# or pass your own question
./mvnw -q compile exec:java -Dexec.args="explain virtual threads"

# other providers
AI_PROVIDER=anthropic ANTHROPIC_API_KEY=sk-ant-... ./mvnw -q compile exec:java
AI_PROVIDER=ollama ./mvnw -q compile exec:java
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

- [`ChatBasic.java`](src/main/java/com/example/cookbook/ChatBasic.java) - one call to `ChatModel.chat(...)`, plus the token usage from the response.
- [`Models.java`](src/main/java/com/example/cookbook/Models.java) - provider selection written out by hand. Every recipe has its own copy,
  because the projects are meant to be readable on their own and copy-pasteable out of the repo.
- [`ChatBasicTest.java`](src/test/java/com/example/cookbook/ChatBasicTest.java) / `StubChatModel.java` - the same check without a key or a network call.

## Gotchas

- `ChatModel` is stateless. Each call sends the full message list you pass it; nothing is stored.
- `chat(String)` is a convenience overload that drops the response metadata. Use
  `chat(ChatMessage...)` when you want `tokenUsage()` or `finishReason()`.
- Ollama needs the model pulled first: `ollama pull llama3.2`.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/01-chat-basic) | [Next: 02 Streaming](../02-streaming)
