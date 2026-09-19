# 05 - Conversation memory (LangChain4j)

**What:** two users talking to the same assistant without seeing each other's history.
`@MemoryId` selects which `ChatMemory` the call uses.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw -q compile exec:java
```

## Where to look

- [`Memory.java`](src/main/java/com/example/cookbook/Memory.java) - `chatMemoryProvider(id -> ...)`, one memory per conversation id.
- [`MemoryTest.java`](src/test/java/com/example/cookbook/MemoryTest.java) - proves the history is resent and that the window drops old messages.

## Gotchas

- Memory is a prompt concern, not a model feature. Every remembered turn is re-sent and re-billed.
- `MessageWindowChatMemory` counts messages, not tokens.
- The window is trimmed before the new message is appended, so a request can carry `maxMessages + 1`.
- With `chatMemory(...)` instead of `chatMemoryProvider(...)` every caller shares one history.
  That is fine for a CLI and wrong for a service.
- The default store is in-memory. `PersistentChatMemoryStore` is an interface you implement -
  there is no database-backed one in the box.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/05-memory) | [Previous: 04 Tool calling](../04-tool-calling) | [Next: 06 Embeddings](../06-embeddings)
