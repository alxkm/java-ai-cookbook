# 08 - RAG with pgvector (LangChain4j)

**What:** the same pipeline as recipe 07 with the vectors in Postgres. What changes once the store
survives a restart: schema ownership, idempotent ingestion, and metadata filtering.

## Run

```bash
docker compose up -d
export OPENAI_API_KEY=sk-...
./mvnw -q compile exec:java
```

Run it twice. The second run prints `handbook.md: up to date` instead of duplicating the corpus.
Edit the handbook and run it again: it prints `reindexed`, and the old text is gone from the table.

## Test

```bash
./mvnw test
```

Testcontainers starts a real pgvector instance and the test uses a fake embedding model, so the
SQL and the metadata filter are exercised without an API key. Without Docker the test is skipped.

## Where to look

- [`RagPgvector.java`](src/main/java/com/example/cookbook/RagPgvector.java) - `PgVectorEmbeddingStore` and `ingestOnce`, the part recipe 07 does not need.
- [`RagPgvectorTest.java`](src/test/java/com/example/cookbook/RagPgvectorTest.java) - similarity search and a `MetadataFilterBuilder` filter against the real
  database.

## Gotchas

- `dimension` has to match the embedding model, which is why it is read from
  `embeddingModel.dimension()` rather than written as a number. A mismatch fails on INSERT, not at
  startup: *"expected 1536 dimensions, not 768"*. The table also keeps the width it was created
  with, so switching embedding models later means dropping it.
- This recipe and the Spring AI one use the same database, user and table name with different
  schemas. Run one at a time, or `docker compose down -v` in between.
- `createTable(true)` is a convenience for a demo. In production the table is yours: create it in a
  migration and leave this off.
- Metadata filtering only works on keys that exist in the stored metadata. A typo in a key name
  returns an empty result instead of an error.
- Re-running ingestion without a guard silently doubles every chunk, and retrieval quality drops
  because the top-k fills up with duplicates.
- **The obvious guard is half a guard.** "Skip if this source is already indexed" stops the
  doubling, and then never lets the document change: edit a line in the handbook and every start
  after that skips, and the app keeps answering from the old text. Each chunk here carries a
  `source_hash` of the document it came from; a different hash deletes that source and indexes it
  again, and leaves other sources alone.
- **`minScore` is a relevance score, `(cosine + 1) / 2`, not a cosine.** A bare `0.4` means
  cosine -0.2, which lets through chunks with no similarity at all. Write it as
  `RelevanceScore.fromCosineSimilarity(0.4)` so the scale is in the code. Spring AI's
  `similarityThreshold` is a raw cosine, so the same number means something different on the
  two sides.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/08-rag-pgvector) | [Previous: 07 RAG, minimal](../07-rag-minimal) | [Next: 09 RAG, advanced](../09-rag-advanced)
