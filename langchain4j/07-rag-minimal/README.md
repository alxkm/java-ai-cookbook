# 07 - RAG, minimal (LangChain4j)

**What:** the five steps of RAG with nothing else in the file - load a markdown handbook, split it,
embed it into an in-memory store, retrieve the top matches, answer from them.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw -q compile exec:java
./mvnw -q compile exec:java -Dexec.args="who approves a payments change"
```

The third default question ("What is the refund policy?") is not in the handbook on purpose.
A correct run says so instead of inventing an answer.

## Where to look

- [`RagMinimal.java`](src/main/java/com/example/cookbook/RagMinimal.java) - `EmbeddingStoreIngestor` for the write side, `EmbeddingStoreContentRetriever`
  for the read side, and `AiServices.contentRetriever(...)` to wire it into the call.
- [`src/main/resources/docs/handbook.md`](src/main/resources/docs/handbook.md) - the corpus. Edit it and the answers change.
- [`RagMinimalTest.java`](src/test/java/com/example/cookbook/RagMinimalTest.java) - asserts the right chunk lands in the prompt, using a keyword-based fake
  embedding model. No key, no network.

## Gotchas

- `minScore` is the setting people forget. Without it the retriever always returns something,
  and "something" becomes the answer.
- **It is also the setting people get wrong, and this recipe did.** `minScore` is a relevance
  score, `(cosine + 1) / 2`, not a cosine - so the `0.4` it used to have meant cosine -0.2 and
  let through chunks with *zero* similarity: "what is the capital of France" came back with three
  chunks of handbook. It is now `RelevanceScore.fromCosineSimilarity(0.4)`, which puts the scale
  in the code. Spring AI's `similarityThreshold` is a raw cosine, so the same number means
  something different on the two sides.
- `DocumentSplitters.recursive(500, 100)` - the second number is the overlap. Zero overlap cuts
  sentences in half at chunk boundaries and costs you recall.
- `InMemoryEmbeddingStore` re-embeds the whole corpus on every start. Fine for a demo, not for a
  service - see recipe 08.
- The `@SystemMessage` telling the model to stay in the context is doing real work. Remove it and
  the model fills the gaps from memory.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/07-rag-minimal) | [Previous: 06 Embeddings](../06-embeddings) | [Next: 08 RAG with pgvector](../08-rag-pgvector)
