# 07 - RAG, minimal (Spring AI)

**What:** the five steps of RAG with nothing else in the file - load a markdown handbook, split it,
embed it into an in-memory store, retrieve the top matches, answer from them.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
./mvnw spring-boot:run -Dspring-boot.run.arguments="who approves a payments change"
```

The third default question ("What is the refund policy?") is not in the handbook on purpose.
A correct run says so instead of inventing an answer.

## Where to look

- [`RagMinimalApplication.java`](src/main/java/com/example/cookbook/RagMinimalApplication.java) - `TextReader` -> `TokenTextSplitter` -> `SimpleVectorStore` ->
  `QuestionAnswerAdvisor`. Each step is one line.
- [`src/main/resources/docs/handbook.md`](src/main/resources/docs/handbook.md) - the corpus. Edit it and the answers change.
- [`RagMinimalTest.java`](src/test/java/com/example/cookbook/RagMinimalTest.java) - asserts that the right chunk lands in the prompt, using a keyword-based
  fake embedding model. No key, no network.

## Gotchas

- `similarityThreshold` is the setting people forget. Without it, the store always returns
  something, and "something" becomes the answer.
- Chunk size is a retrieval decision, not a formatting one. Chunks that are too large dilute the
  match; too small and they lose the context that made them meaningful.
- `SimpleVectorStore` re-embeds the whole corpus on every start. Fine for a demo, not for a service -
  see recipe 08.
- The system prompt telling the model to stay in the context is doing real work here. Remove it
  and the model fills the gaps from memory.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/07-rag-minimal) | [Previous: 06 Embeddings](../06-embeddings) | [Next: 08 RAG with pgvector](../08-rag-pgvector)
