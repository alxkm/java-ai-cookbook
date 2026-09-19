# 09 - RAG, advanced (Spring AI)

**What:** recipe 07 plus the three things that actually move retrieval quality - query rewriting,
a metadata filter, and re-ranking - and a refusal path when nothing relevant comes back.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
./mvnw spring-boot:run -Dspring-boot.run.arguments="can I deploy during the freeze"
```

Two sources are indexed, `handbook.md` and `runbook.md`, but the retriever is filtered to the
handbook. The third default question is answered from the runbook, so it gets refused - that is
the filter doing its job, not a bug.

## Where to look

- [`RagAdvancedApplication.java`](src/main/java/com/example/cookbook/RagAdvancedApplication.java) - `RetrievalAugmentationAdvisor` with a query transformer, a
  filtered retriever, a post-processor and an augmenter. Each stage is one builder call.
- [`KeywordRerankProcessor.java`](src/main/java/com/example/cookbook/KeywordRerankProcessor.java) - re-ranking without a re-ranking model, so the idea is visible.
- [`RagAdvancedTest.java`](src/test/java/com/example/cookbook/RagAdvancedTest.java) - the reranker on its own, and the filter end to end.

## Gotchas

- Over-fetch before you re-rank. `topK(3)` into a re-ranker gives it nothing to work with;
  `topK(8)` down to 3 is the point.
- `RewriteQueryTransformer` costs an extra model call per question. It earns it on conversational
  follow-ups and wastes money on already-specific queries. On a local 3B model it roughly doubles
  the wall clock: three questions took over ten minutes on Ollama, against seconds on a hosted model.
- `allowEmptyContext(false)` is what turns "no results" into "I do not know" instead of a confident
  guess from pre-training.
- A filter expression on a field that is not in the metadata returns nothing, quietly. Set the
  metadata at ingest time, in one place.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/09-rag-advanced) | [Previous: 08 RAG with pgvector](../08-rag-pgvector) | [Next: 10 Agent, ReAct loop](../10-agent-react)
