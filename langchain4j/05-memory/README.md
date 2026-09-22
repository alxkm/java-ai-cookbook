# 05 - Conversation memory (LangChain4j)

**What:** two users talking to the same assistant without seeing each other's history, and
history that is still there after a restart. `@MemoryId` selects which `ChatMemory` the call
uses; a `ChatMemoryStore` decides where its messages live.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw -q compile exec:java
```

Run it a second time and Alice already knows what she works on - the turns came back from H2,
not from this process.

## Where to look

- [`Memory.java`](src/main/java/com/example/cookbook/Memory.java) - `chatMemoryProvider(id -> ...)`, one memory per conversation id, each built
  over the shared store.
- [`JdbcChatMemoryStore.java`](src/main/java/com/example/cookbook/JdbcChatMemoryStore.java) - the three methods the interface asks for, in plain JDBC.
- [`MemoryTest.java`](src/test/java/com/example/cookbook/MemoryTest.java) - proves the history is resent and that the window drops old messages.
- [`JdbcChatMemoryStoreTest.java`](src/test/java/com/example/cookbook/JdbcChatMemoryStoreTest.java) - writes through one store and reads back through another over the
  same database, which is as close to a restart as a test gets.

## Gotchas

- Memory is a prompt concern, not a model feature. Every remembered turn is re-sent and re-billed.
- `MessageWindowChatMemory` counts messages, not tokens.
- The window is trimmed before the new message is appended, so a request can carry `maxMessages + 1`.
- With `chatMemory(...)` instead of `chatMemoryProvider(...)` every caller shares one history.
  That is fine for a CLI and wrong for a service.
- **The window and the storage are two separate decisions**, and leaving the store out quietly
  makes one choice for both: the history lives in a map that dies with the process, and looks
  identical in every test that does not restart anything.
- The interface is `ChatMemoryStore` and the only implementation in the box is
  `InMemoryChatMemoryStore`. Persistence is three methods you write - which is the whole
  difference from the Spring AI side, where the same capability is a starter and a generated
  schema.
- `updateMessages` hands over the entire list every time, not the new message. A store that
  inserts instead of replacing doubles every conversation, and nothing about the signature warns
  you.
- Serialise with `ChatMessageSerializer`, not your own JSON. Roll your own and the role, the
  tool calls, or the difference between a user and an assistant turn is what goes missing.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/05-memory) | [Previous: 04 Tool calling](../04-tool-calling) | [Next: 06 Embeddings](../06-embeddings)
