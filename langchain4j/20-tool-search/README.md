# 20 - Progressive tool disclosure (LangChain4j)

**What:** twenty tools registered, at most three reaching the model on any turn. `ToolProvider` is
asked once per request which tools the model may see, and the answer is computed from the user
message - the selection happens in code, before the call.

## Run

```bash
export OPENAI_API_KEY=sk-...        # or: export OLLAMA_BASE_URL=http://localhost:11434
./mvnw -q compile exec:java
```

```
20 tools registered, at most 3 reach the model per turn
> The customer wants their money back for order A-1001.
```

## Where to look

- [`KeywordToolProvider.java`](src/main/java/com/example/cookbook/KeywordToolProvider.java) - `provideTools(request)`, the whole mechanism. Ranking by term
  overlap, capped, and returning nothing when nothing matches.
- [`ToolSearch.java`](src/main/java/com/example/cookbook/ToolSearch.java) - `toolProvider(...)` rather than `tools(...)`, which is what makes the
  list per-request instead of fixed at build time.
- [`KeywordToolProviderTest.java`](src/test/java/com/example/cookbook/KeywordToolProviderTest.java) - what a question actually selects, and what it leaves out.

## Gotchas

- **The selection is yours, including the bad defaults.** Falling back to the whole catalogue when
  nothing matches is the tempting line of code, and it undoes the recipe on exactly the requests
  where the prompt is already longest. Sending nothing is the honest answer.
- **Return the executors, not just the specifications.** A `ToolProviderResult` with specifications
  and no executor compiles, advertises the tools and fails at call time - a long way from where the
  mistake was made.
- Split camelCase names before matching. Most of the signal is in `stockLevel`, not in the prose
  around it, and "stock level please" misses it otherwise.
- `Set.of` on tokenised text throws rather than deduplicating, and a word in both the tool name and
  its description is a duplicate. Collect to a set instead - found the hard way, which is why
  `terms()` says so.
- **A synonym is a miss.** "Give me my money back please" selects *nothing* - the refund tool is
  right there and term overlap has no idea that "money back" means refund. Same for "where has
  my package got to", because the tools say parcel. Worse than empty is the near miss: "the
  customer wants their money back" selects the customer-orders lookup, which is plausible and
  wrong. All three asserted in `aSynonymIsAMiss`. Embeddings are the usual answer.
- That is the real trade against the Spring AI side of this recipe. There the *model* writes the
  search query, so it can look for "refund" when the user said "money back"; here the match is
  against the user's literal words. One round trip instead of two, and it only knows what the
  user already said.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/20-tool-search) | [Previous: 19 Retry and rate limits](../19-retry)
