# 06 - Embeddings (Spring AI)

**What:** turn four sentences and a question into vectors, then rank the sentences by cosine
similarity to the question. This is the retrieval half of RAG with nothing else in the way.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
./mvnw spring-boot:run -Dspring-boot.run.arguments="how do I make coffee"

# local, no key
EMBEDDING_PROVIDER=ollama ./mvnw spring-boot:run
```

## Where to look

- [`EmbeddingsApplication.java`](src/main/java/com/example/cookbook/EmbeddingsApplication.java) - `rank(...)`: `embedForResponse(list)` embeds the whole corpus in one
  call, and each result is paired with its text by `getIndex()`, not by its position in the list.
- [`RankTest.java`](src/test/java/com/example/cookbook/RankTest.java) - the batching and the pairing, asserted: results returned out of order,
  and a provider that drops a text.
- [`Similarity.java`](src/main/java/com/example/cookbook/Similarity.java) - cosine similarity in four lines, so it stops being a black box.

## Gotchas

- Anthropic has no embedding endpoint. This recipe uses `EMBEDDING_PROVIDER` (openai or ollama)
  rather than `AI_PROVIDER`.
- Vectors from different models are not comparable. Re-embed the whole corpus when you switch
  models or dimensions - a mixed index returns nonsense, silently.
- Embed in batches. One call per document is the usual reason an ingest job takes minutes.
- `text-embedding-3-small` returns 1536 dimensions, `nomic-embed-text` returns 768. Any vector
  column you create has to match.
- **Pair results by `getIndex()`, not by position.** Each `Embedding` carries the index of the text
  it came from, and the list order is not a promise. Paired by position, a reordered response
  labels every text with another text's score, and nothing looks wrong.
- Check the result count. A provider that skips a text - an empty string, one over the token
  limit - returns fewer results than it was given, and everything after the gap shifts by one.
- `dimensions()` is not free. Unless the provider hardcodes it, the default embeds a test string -
  one more billed call. Read the length off a vector you already have.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/06-embeddings) | [Previous: 05 Conversation memory](../05-memory) | [Next: 07 RAG, minimal](../07-rag-minimal)
