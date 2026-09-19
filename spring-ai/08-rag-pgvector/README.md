# 08 - RAG with pgvector (Spring AI)

**What:** the same pipeline as recipe 07 with the vectors in Postgres. What changes once the store
survives a restart: schema ownership, idempotent ingestion, and metadata filtering.

## Run

Docker is required. `spring-boot-docker-compose` starts `compose.yaml` for you.

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

Run it twice. The second run prints `already indexed` instead of duplicating the corpus.

## Test

```bash
./mvnw test
```

Testcontainers starts a real pgvector instance and the test uses a fake embedding model, so the
SQL, the index and the filter are exercised without an API key. Without Docker the test is skipped.

## Where to look

- [`RagPgvectorApplication.java`](src/main/java/com/example/cookbook/RagPgvectorApplication.java) - `ingestOnce`, which is the part recipe 07 does not need.
- [`application.yml`](src/main/resources/application.yml) - `initialize-schema`, `index-type: hnsw`, `dimensions: 1536`.
- [`RagPgvectorTest.java`](src/test/java/com/example/cookbook/RagPgvectorTest.java) - similarity search and a metadata filter against the real database.

## Gotchas

- **A custom image needs a service-connection label.** Boot recognises a compose service by its
  image name and it does not know `pgvector/pgvector`. Without
  `org.springframework.boot.service-connection: postgres` in `compose.yaml` the container starts
  perfectly and the datasource is never configured, which surfaces as the very unhelpful
  *"Failed to determine a suitable driver class"*.
- **If Docker is not running you get the same message**, because `logging.level.root: WARN` hides
  the compose lifecycle lines. Start Docker first, or drop the log level when it misbehaves.
- `dimensions` is deliberately not set here, so the store asks the embedding model how wide its
  vectors are and the recipe works on either provider. Pin it only when you own the schema, and
  then it has to match exactly: 1536 for `text-embedding-3-small`, 768 for `nomic-embed-text`.
  A mismatch fails on INSERT, not at startup - *"expected 1536 dimensions, not 768"*.
- This recipe and the LangChain4j one use the same database, user and table name with different
  schemas. Run one at a time, or `docker compose down -v` in between.
- `initialize-schema: true` is a convenience for a demo. In production the table is yours: create
  it in a migration and leave this off. Note the table keeps whatever vector width it was created
  with, so switching embedding models later means dropping it.
- HNSW indexes are built in memory and are slow to create on a large table. Ingest first, index after.
- Re-running ingestion without a guard silently doubles every chunk, and retrieval quality drops
  because the top-k fills up with duplicates.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/08-rag-pgvector) | [Previous: 07 RAG, minimal](../07-rag-minimal) | [Next: 09 RAG, advanced](../09-rag-advanced)
