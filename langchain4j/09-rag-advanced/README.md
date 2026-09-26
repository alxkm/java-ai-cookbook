# 09 - RAG, advanced (LangChain4j)

**What:** recipe 07 plus the three things that actually move retrieval quality - query expansion,
a metadata filter, and re-ranking.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw -q compile exec:java
./mvnw -q compile exec:java -Dexec.args="can I deploy during the freeze"
```

Two sources are indexed, `handbook.md` and `runbook.md`, but the retriever is filtered to the
handbook. The third default question is answered from the runbook, so it gets refused - that is
the filter doing its job, not a bug.

## Where to look

- [`RagAdvanced.java`](src/main/java/com/example/cookbook/RagAdvanced.java) - `DefaultRetrievalAugmentor` assembled from a query transformer, a filtered
  retriever and a re-ranking aggregator.
- [`KeywordScoringModel.java`](src/main/java/com/example/cookbook/KeywordScoringModel.java) - a `ScoringModel` with no model behind it, so re-ranking is visible.
- [`RagAdvancedTest.java`](src/test/java/com/example/cookbook/RagAdvancedTest.java) - the scoring model on its own, and the filter end to end.

## Gotchas

- Over-fetch before you re-rank. `maxResults(3)` into an aggregator gives it nothing to work with;
  8 down to 3 is the point.
- `ExpandingQueryTransformer` costs an extra model call and then one retrieval per variant. It earns
  it on vague questions and wastes money on specific ones.
- **A query transformer and a re-ranker do not compose by default.** Expanding the query produces
  several queries, and `ReRankingContentAggregator` then throws rather than guess which one to
  score against: *"the 'queryToContents' contains 3 queries, making the re-ranking ambiguous"*.
  Pass a `querySelector`. Nothing warns you until both are in the pipeline at once.
- `ReRankingContentAggregator.minScore` is what drops weak matches entirely. Without it the
  aggregator only reorders, and the weakest chunk still reaches the prompt.
- A filter on a metadata key that was never set returns nothing, quietly. Set the metadata at
  ingest time, in one place.
- **`minScore` is a relevance score, `(cosine + 1) / 2`, not a cosine.** A bare `0.4` means
  cosine -0.2, which lets through chunks with no similarity at all. Write it as
  `RelevanceScore.fromCosineSimilarity(0.4)` so the scale is in the code. Spring AI's
  `similarityThreshold` is a raw cosine, so the same number means something different on the
  two sides.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/09-rag-advanced) | [Previous: 08 RAG with pgvector](../08-rag-pgvector) | [Next: 10 Agent, ReAct loop](../10-agent-react)
