# 05 - Conversation memory (Spring AI)

**What:** two users talking to the same `ChatClient` without seeing each other's history.
`MessageChatMemoryAdvisor` replays the stored turns into every request.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

Alice and Bob each introduce themselves, then each asks what they work on. Both get the right answer.

## Where to look

- [`MemoryApplication.java`](src/main/java/com/example/cookbook/MemoryApplication.java) - the `ChatMemory` bean, the advisor, and `ChatMemory.CONVERSATION_ID`
  passed per call.
- [`MemoryTest.java`](src/test/java/com/example/cookbook/MemoryTest.java) - proves the history is resent and that the window really drops old messages.

## Gotchas

- Memory is a prompt concern, not a model feature. Every remembered turn is re-sent and re-billed
  on every request.
- `MessageWindowChatMemory` counts messages, not tokens. Ten long messages can still blow past the
  context window.
- Without a conversation id all callers share the `default` conversation. That is the bug you will
  ship if you forget the `advisors(...)` line.
- The default store is in-memory and dies with the process. Swap in
  `spring-ai-starter-model-chat-memory-repository-jdbc` for a `ChatMemoryRepository` backed by a database.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/05-memory) | [Previous: 04 Tool calling](../04-tool-calling) | [Next: 06 Embeddings](../06-embeddings)
