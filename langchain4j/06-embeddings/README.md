# 06 - Embeddings (LangChain4j)

**What:** turn four sentences and a question into vectors, then rank the sentences by cosine
similarity to the question. The retrieval half of RAG, with nothing else in the way.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw -q compile exec:java
./mvnw -q compile exec:java -Dexec.args="how do I make coffee"

# local, no key
AI_PROVIDER=ollama ./mvnw -q compile exec:java
```

## Where to look

- [`Embeddings.java`](src/main/java/com/example/cookbook/Embeddings.java) - `rank(...)`: `embedAll(segments)` for the corpus, `embed(text)` for
  the query, `CosineSimilarity.between(...)` for the ranking.
- [`RankTest.java`](src/test/java/com/example/cookbook/RankTest.java) - the batching asserted as batch sizes, and a provider that drops a text.
- [`Models.java`](src/main/java/com/example/cookbook/Models.java) - `embedding()` falls back to OpenAI or Ollama when `AI_PROVIDER=anthropic`, because
  Anthropic has no embedding endpoint.

## Gotchas

- Vectors from different models are not comparable. Re-embed the whole corpus when you switch
  models - a mixed index returns nonsense, silently.
- `embedAll` is one HTTP call. Looping over `embed` is the usual reason an ingest job is slow.
- `text-embedding-3-small` returns 1536 dimensions, `nomic-embed-text` returns 768.
- Ollama needs the embedding model pulled separately: `ollama pull nomic-embed-text`.
- **A LangChain4j `Embedding` has no index.** The order of `embedAll`'s result is the only thing
  tying a vector to its text, so check the count: a provider that skips one text shifts every
  later text onto its neighbour's score, silently. The Spring side can pair by `getIndex()`; this
  side cannot, which makes the check the whole safeguard.
- `dimension()` is not free. The default embeds the string `"test"` - one more billed call - on
  any model that does not override it. Read it off a vector you already have.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/06-embeddings) | [Previous: 05 Conversation memory](../05-memory) | [Next: 07 RAG, minimal](../07-rag-minimal)
