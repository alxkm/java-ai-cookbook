# Choosing a model

Short version: start with a small hosted model, measure with recipe 17 ([Spring AI](../spring-ai/17-evals), [LangChain4j](../langchain4j/17-evals)), and only move up when the
evals tell you to. Most tasks in a backend service are classification, extraction and routing, and
those do not need a frontier model.

## The three defaults in this repo

| Provider | Chat model | Embedding model | When it is the right call |
|----------|------------|-----------------|---------------------------|
| OpenAI | `gpt-4o-mini` | `text-embedding-3-small` | Default. Cheap, fast, good enough for most of these recipes. |
| Anthropic | `claude-sonnet-5` | none | Long documents, careful instruction following, tool use over many steps. |
| Ollama | `llama3.2` | `nomic-embed-text` | No key, no network, no per-token cost. Everything else is a trade-off. |

Anthropic has no embedding endpoint. Recipes [06](../spring-ai/06-embeddings), [07](../spring-ai/07-rag-minimal), [08](../spring-ai/08-rag-pgvector) and [09](../spring-ai/09-rag-advanced) fall back to OpenAI or Ollama for
the embedding half, which is why they use `EMBEDDING_PROVIDER` rather than `AI_PROVIDER`.

## What actually drives the decision

**Task shape, not benchmark scores.** Classification, routing and extraction work on small models.
Multi-step reasoning, long tool chains and writing that has to be right first time do not. [Recipe 11](../spring-ai/11-multi-agent)
routes with one model call - that call does not need to be expensive; the desks behind it might.

**Context window.** Not the advertised number, the number you actually pay for. Every RAG chunk,
every remembered turn and every tool result is re-sent on every call. [Recipe 05](../spring-ai/05-memory) and [recipe 10](../spring-ai/10-agent-react) are
where this shows up on the bill.

**Latency.** A hosted small model answers in a second or two. A local 3B model on a laptop answers
in five to fifteen, and the first call after a cold start is worse. If a user is waiting, stream
(recipe 02 ([Spring AI](../spring-ai/02-streaming), [LangChain4j](../langchain4j/02-streaming))) - it does not make the answer faster, it makes the wait legible.

**Structured output reliability.** This is the first thing that degrades on a smaller model, and
recipe 18 ([Spring AI](../spring-ai/18-ollama), [LangChain4j](../langchain4j/18-ollama)) exists because of it. If your whole pipeline depends on JSON coming back, either use a
provider with native schema support or write the retry-and-salvage code once.

**Tool calling reliability.** The second thing to degrade. Not every Ollama model supports it at all,
and the ones that do will invent arguments. Validate everything a tool receives (recipe 04 ([Spring AI](../spring-ai/04-tool-calling), [LangChain4j](../langchain4j/04-tool-calling))).

## Cost, roughly

Prices move, so treat this as shape rather than numbers. [Recipe 16](../spring-ai/16-observability) measures the real figure for your
own traffic, which is the only one that matters.

- Input tokens are cheaper than output tokens, usually by 3-5x.
- A small hosted model is 10-20x cheaper than a frontier one for the same call.
- RAG shifts cost from output to input: you send more, the model writes less.
- An agent loop multiplies everything. Six steps means six calls, each resending the whole history.

The cheapest optimisation is almost never a smaller model. It is sending less: fewer retrieved
chunks, a shorter memory window, a tighter system prompt.

## Embeddings

Embedding models and chat models are separate decisions. The embedding model is a much harder one to
change later, because changing it means re-embedding the whole corpus - vectors from different
models are not comparable, and a mixed index returns nonsense without erroring.

Dimensions have to match your vector column: 1536 for `text-embedding-3-small`, 768 for
`nomic-embed-text`. [Recipe 08](../spring-ai/08-rag-pgvector) fails at insert time if they do not.

## A workable process

1. Build the feature with the cheapest model that plausibly works.
2. Write twenty eval cases from questions your users actually ask, including ones with no answer
   (recipe 17 ([Spring AI](../spring-ai/17-evals), [LangChain4j](../langchain4j/17-evals))).
3. Run them. If the pass rate is fine, you are done - stop here.
4. If it is not, try a better prompt and better retrieval before a bigger model. Both are cheaper
   and both help more often than people expect.
5. Move up a model only when the evals say the failures are reasoning failures.

Model names in this repo are pinned deliberately, in `application.yml` and in [`Models.java`](../langchain4j/01-chat-basic/src/main/java/com/example/cookbook/Models.java). When
you change one, re-run the evals - that is the whole reason they are there.
