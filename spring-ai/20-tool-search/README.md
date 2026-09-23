# 20 - Progressive tool disclosure (Spring AI)

**What:** twenty tools registered, at most three reaching the model on any turn. The model is given
a tool for *searching* tools, so it finds what it needs instead of being handed everything.
Deliberately not here: which index to use in production - that is a retrieval question, and
[recipe 09](../09-rag-advanced) is where retrieval quality is argued about.

## Run

```bash
export OPENAI_API_KEY=sk-...        # or: export OLLAMA_BASE_URL=http://localhost:11434
./mvnw spring-boot:run
```

```
20 tools indexed, at most 3 reach the model per turn
> The customer wants their money back for order A-1001.
```

## Where to look

- [`ToolSearchApplication.java`](src/main/java/com/example/cookbook/ToolSearchApplication.java) - the `ToolIndex` bean, the indexing step, and
  `ToolSearchToolCallingAdvisor` on the `ChatClient`.
- [`application.yml`](src/main/resources/application.yml) - `tool-index-type`, `max-results`, the score threshold and the eviction
  policy, in one place.
- [`ToolIndexTest.java`](src/test/java/com/example/cookbook/ToolIndexTest.java) - what a question actually retrieves, including the case where it
  retrieves the wrong thing.

## Gotchas

- **The advisor is off by default** (`enabled: false`), and **the default index is `regex`** even
  though the starter pulls in Lucene. Adding the dependency is not the same as using it, and
  nothing fails - you just get substring matching and wonder why the shortlist is poor.
- **Indexing is not automatic.** The advisor searches whatever is in the index for that session. An
  empty index does not mean "show everything", it means the model is told there are no tools.
- **The index is keyed by session**, so it grows with conversations rather than with tools. That is
  what the eviction strategies are for; `lru-max-sessions` defaults to 1000.
- A shared verb costs you a slot. `check the invoice` retrieves `stockLevel`, because its
  description starts "Check how many units..." - BM25 has no idea the domains differ. Asserted in
  `ToolIndexTest.aCommonVerbDragsInAToolFromAnotherDomain`. Raise `min-score-threshold`, or use the
  vector index when your descriptions share their verbs.
- `max-results` is a ceiling, not a quota. `refund` returns one tool, not three, because nothing
  else clears the score threshold - which is the behaviour you want and not what the name suggests.
- The index has no idea about synonyms. Queried with a user's literal words, "give me my money
  back please" matches nothing at all and "where has my package got to" matches three unrelated
  tools, because the catalogue says refund and parcel. What saves this recipe is *who writes the
  query*: the model does, so it can search for "refund" when the user said "money back" - and
  that is the whole advantage over selecting tools from the user message in code, which is what
  the [LangChain4j side](../../langchain4j/20-tool-search) does.
- This costs a round trip: the model calls the search tool, then calls the real one. Worth it when
  the catalogue is large, wasteful when it is five tools you could have sent anyway.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/20-tool-search) | [Previous: 19 Retry and rate limits](../19-retry)
