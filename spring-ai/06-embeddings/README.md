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

- [`EmbeddingsApplication.java`](src/main/java/com/example/cookbook/EmbeddingsApplication.java) - `embedForResponse(list)` embeds the whole corpus in one call.
- [`Similarity.java`](src/main/java/com/example/cookbook/Similarity.java) - cosine similarity in four lines, so it stops being a black box.

## Gotchas

- Anthropic has no embedding endpoint. This recipe uses `EMBEDDING_PROVIDER` (openai or ollama)
  rather than `AI_PROVIDER`.
- Vectors from different models are not comparable. Re-embed the whole corpus when you switch
  models or dimensions - a mixed index returns nonsense, silently.
- Embed in batches. One call per document is the usual reason an ingest job takes minutes.
- `text-embedding-3-small` returns 1536 dimensions, `nomic-embed-text` returns 768. Any vector
  column you create has to match.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/06-embeddings) | [Previous: 05 Conversation memory](../05-memory) | [Next: 07 RAG, minimal](../07-rag-minimal)
