# java-ai-cookbook

Runnable, minimal LLM examples for Java - **Spring AI** & **LangChain4j**.
RAG, agents, MCP servers, tool calling, structured output, evals.

**One folder = one command.** Every example is isolated, has its own build, and runs in under a minute.

[![build](https://github.com/alxkm/java-ai-cookbook/actions/workflows/build.yml/badge.svg?branch=main&event=push)](https://github.com/alxkm/java-ai-cookbook/actions/workflows/build.yml)
[![Java](https://img.shields.io/badge/Java-21+-blue)](https://adoptium.net)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.x-6DB33F)](https://docs.spring.io/spring-ai/reference/)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-1.x-orange)](https://docs.langchain4j.dev)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## Why this exists

Most "AI in Java" examples are either a single hello-world or a full demo app with 40 dependencies.
This repo sits in between: each recipe shows **one pattern**, in **one file where possible**, with **no framework magic hidden**.

- Same pattern implemented in **both** Spring AI and LangChain4j, so you can compare them honestly
- Works with **OpenAI, Anthropic, and local models via Ollama** - switch with an env var
- Every recipe has a test, so the examples don't silently rot

## Quick start

```bash
git clone https://github.com/alxkm/java-ai-cookbook.git
cd java-ai-cookbook/spring-ai/01-chat-basic

export OPENAI_API_KEY=sk-...        # or: export OLLAMA_BASE_URL=http://localhost:11434
./mvnw spring-boot:run
```

That's it. Open the folder of any recipe and run the command in its README.

## Recipes

| # | Pattern | Spring AI | LangChain4j | What you learn |
|---|---------|:---------:|:-----------:|----------------|
| 01 | Chat basics | [→](spring-ai/01-chat-basic) | [→](langchain4j/01-chat-basic) | ChatClient / ChatModel, system prompt, model switching |
| 02 | Streaming | [→](spring-ai/02-streaming) | [→](langchain4j/02-streaming) | Token streaming to console and SSE endpoint |
| 03 | Structured output | [→](spring-ai/03-structured-output) | [→](langchain4j/03-structured-output) | JSON → Java record, schema enforcement, retries |
| 04 | Tool calling | [→](spring-ai/04-tool-calling) | [→](langchain4j/04-tool-calling) | Expose Java methods as tools, multi-step calls |
| 05 | Conversation memory | [→](spring-ai/05-memory) | [→](langchain4j/05-memory) | In-memory vs persisted history, token windows |
| 06 | Embeddings | [→](spring-ai/06-embeddings) | [→](langchain4j/06-embeddings) | Embedding models, similarity, batching |
| 07 | RAG - minimal | [→](spring-ai/07-rag-minimal) | [→](langchain4j/07-rag-minimal) | Load → split → embed → retrieve → answer |
| 08 | RAG - pgvector | [→](spring-ai/08-rag-pgvector) | [→](langchain4j/08-rag-pgvector) | Production vector store with Testcontainers |
| 09 | RAG - advanced | [→](spring-ai/09-rag-advanced) | [→](langchain4j/09-rag-advanced) | Query rewriting, re-ranking, metadata filters |
| 10 | Agent - ReAct loop | [→](spring-ai/10-agent-react) | [→](langchain4j/10-agent-react) | Planning, tool selection, stopping conditions |
| 11 | Multi-agent | [→](spring-ai/11-multi-agent) | [→](langchain4j/11-multi-agent) | Orchestrator + workers, hand-offs |
| 12 | MCP client | [→](spring-ai/12-mcp-client) | [→](langchain4j/12-mcp-client) | Consume tools from an MCP server |
| 13 | MCP server | [→](spring-ai/13-mcp-server) | [→](langchain4j/13-mcp-server) | Expose your Java service as an MCP server |
| 14 | Multimodal | [→](spring-ai/14-multimodal) | [→](langchain4j/14-multimodal) | Images in, text out |
| 15 | Guardrails | [→](spring-ai/15-guardrails) | [→](langchain4j/15-guardrails) | Input/output validation, PII, prompt injection |
| 16 | Observability | [→](spring-ai/16-observability) | [→](langchain4j/16-observability) | Tracing, token/cost metrics, Micrometer |
| 17 | Evals | [→](spring-ai/17-evals) | [→](langchain4j/17-evals) | LLM-as-judge, regression tests for prompts |
| 18 | Local models | [→](spring-ai/18-ollama) | [→](langchain4j/18-ollama) | Ollama, no API key, offline |
| 19 | Retry & rate limits | [→](spring-ai/19-retry) | [→](langchain4j/19-retry) | 429s, backoff with jitter, a wall-clock budget |
| 20 | Progressive tool disclosure | [→](spring-ai/20-tool-search) | [→](langchain4j/20-tool-search) | Hundreds of tools without hundreds of tool definitions |

More recipes are added regularly - see [open issues](../../issues) for what's next, or propose one.

## Docs

- [Spring AI vs LangChain4j](docs/spring-ai-vs-langchain4j.md) - a side-by-side comparison written
  after implementing all 18 patterns twice, with links into the code for every claim.
- [Choosing a model](docs/choosing-a-model.md) - which model for which recipe, what it costs, and
  what breaks when you go smaller.
- [CONTRIBUTING.md](CONTRIBUTING.md) - the recipe template and the conventions.

## Repository layout

```
java-ai-cookbook/
├── spring-ai/
│   ├── 01-chat-basic/          # independent Maven project
│   │   ├── README.md           # what it shows, how to run, what to look at
│   │   ├── pom.xml
│   │   └── src/
│   └── ...
├── langchain4j/
│   └── ...                     # same numbering, same patterns
├── docs/
│   ├── spring-ai-vs-langchain4j.md   # honest side-by-side comparison
│   └── choosing-a-model.md
├── .github/workflows/build.yml       # one CI job per recipe, no API keys
├── tools/                            # run-all-tests, link and config-key checks
├── CONTRIBUTING.md
└── .env.example
```

Each recipe README follows the same shape: **What / Run / Where to look / Gotchas**.

## Configuration

All recipes read the same environment variables:

| Variable | Purpose |
|----------|---------|
| `OPENAI_API_KEY` | Use OpenAI models (default provider) |
| `ANTHROPIC_API_KEY` | Use Anthropic models |
| `OLLAMA_BASE_URL` | Use a local Ollama instance |
| `AI_PROVIDER` | `openai` \| `anthropic` \| `ollama` - overrides auto-detection |
| `EMBEDDING_PROVIDER` | `openai` \| `ollama` - used by the embedding and RAG recipes, because Anthropic has no embedding endpoint |

Copy `.env.example` to `.env` and fill in what you have. Nothing else is required.

## Requirements

- Java 21+
- Docker (only for recipes marked with pgvector / Testcontainers)
- One of: an OpenAI key, an Anthropic key, or Ollama running locally

## Contributing

Recipes are welcome. Keep them small, keep them runnable, keep them tested.
See [CONTRIBUTING.md](CONTRIBUTING.md) for the recipe template.

## License

[MIT](LICENSE)
