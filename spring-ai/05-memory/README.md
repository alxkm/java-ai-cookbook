# 05 - Conversation memory (Spring AI)

**What:** two users talking to the same `ChatClient` without seeing each other's history, and
history that is still there after a restart. `MessageChatMemoryAdvisor` replays the stored turns
into every request; a `ChatMemoryRepository` decides where they were stored.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

Alice and Bob each introduce themselves, then each asks what they work on. Both get the right
answer. Run it a second time and Alice already knows - the turns came back from H2, not from
this process.

## Where to look

- [`MemoryApplication.java`](src/main/java/com/example/cookbook/MemoryApplication.java) - the `ChatMemory` bean taking a `ChatMemoryRepository`, the advisor,
  and `ChatMemory.CONVERSATION_ID` passed per call.
- [`application.yml`](src/main/resources/application.yml) - the datasource and `initialize-schema`. Point the url at a real database and
  nothing in the Java changes.
- [`MemoryTest.java`](src/test/java/com/example/cookbook/MemoryTest.java) - proves the history is resent and that the window really drops old messages.
- [`PersistedMemoryTest.java`](src/test/java/com/example/cookbook/PersistedMemoryTest.java) - writes through one `ChatMemory` and reads back through another over
  the same repository, which is as close to a restart as a test gets.

## Gotchas

- Memory is a prompt concern, not a model feature. Every remembered turn is re-sent and re-billed
  on every request.
- `MessageWindowChatMemory` counts messages, not tokens. Ten long messages can still blow past the
  context window.
- Without a conversation id all callers share the `default` conversation. That is the bug you will
  ship if you forget the `advisors(...)` line.
- **The window and the storage are two separate decisions**, and the default wiring makes one
  choice for both. `MessageWindowChatMemory` without a repository keeps everything in memory and
  loses it on restart, while looking identical in every test that does not restart anything.
- `initialize-schema` is unset by default, which means the repository assumes someone else owns
  the schema and fails on the first query rather than at startup. Set it, or run the shipped
  `schema-*.sql` yourself - there is one per dialect inside the jar.
- The table carries both a `timestamp` and a `sequence_id`, and the second one is why ordering
  survives: two messages in the same millisecond have the same timestamp, which happens on every
  fast turn.
- Conversation rows are yours to list and delete. `findConversationIds` and
  `deleteByConversationId` are the whole retention story, and nobody calls them for you.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/05-memory) | [Previous: 04 Tool calling](../04-tool-calling) | [Next: 06 Embeddings](../06-embeddings)
