# Spring AI vs LangChain4j

Both libraries do the same job. They disagree about where the abstraction belongs, and that
disagreement is what you are actually choosing between.

Everything below comes from writing the same 18 recipes twice. Where one library needed more code,
it is noted; where one needed less code but hid something, that is noted too.

## The one-line version

**Spring AI** puts the AI call inside the Spring programming model: beans, auto-configuration,
properties, advisors, observations. If your service is already Spring Boot, most of it is
configuration you already know how to write.

**LangChain4j** is a plain Java library with no container. You construct objects and call methods.
If you are not on Spring - Quarkus, Micronaut, a CLI, a library - it stays out of your way.

Neither is the "advanced" one. They are the same distance from the provider API, approached from
opposite directions.

## Where they actually differ

### Configuration vs construction

Spring AI switches providers with a property (`spring.ai.model.chat`), and the starters on the
classpath decide what is available. [Recipe 01](../spring-ai/01-chat-basic) has all three providers wired at once and picks one
at startup.

The cost of that convenience is that auto-configuration builds more than you asked for. The OpenAI
starter configures embedding, image, speech, transcription and moderation models too, and each one
demands a key at startup - so `spring.ai.model.chat=ollama` alone is not enough to run without an
OpenAI key. Every Spring AI recipe here sets the unused model types to `none` for that reason.

LangChain4j has no configuration layer. Every recipe here carries a [`Models.java`](../langchain4j/01-chat-basic/src/main/java/com/example/cookbook/Models.java) that reads env
vars and builds the model by hand - about 50 lines. That is a real cost, and it is also why there
is nothing to debug when it goes wrong: nothing is constructed that the code does not name.

### The unit of composition

Spring AI composes with **advisors**: a chain around the call, with ordering, shared context, and
both a call and a stream path. Memory, RAG and guardrails are all advisors ([05](../spring-ai/05-memory), [07](../spring-ai/07-rag-minimal), [15](../spring-ai/15-guardrails)).
Once you have written one, you have written all of them.

LangChain4j composes with **typed interfaces**: `AiServices` turns an interface into the call, and
the pieces hang off the builder (`chatMemory`, `contentRetriever`, `tools`, `toolProvider`,
`inputGuardrails`). The return type of the method is the contract, which is how structured output
([recipe 03](../langchain4j/03-structured-output)) stops being a parsing problem.

The practical difference shows up when you need something the library did not anticipate. Writing a
custom advisor ([recipe 15, Spring AI](../spring-ai/15-guardrails)) and a custom guardrail ([recipe 15, LangChain4j](../langchain4j/15-guardrails)) are both easy.
Writing a custom *retrieval pipeline* is easier in LangChain4j, because `DefaultRetrievalAugmentor`
is assembled from parts you can replace one at a time ([recipe 09](../langchain4j/09-rag-advanced)).

### Where the loop lives

Both run the tool-calling loop for you (recipe 04 ([Spring AI](../spring-ai/04-tool-calling), [LangChain4j](../langchain4j/04-tool-calling))). Both let you take it back (recipe 10 ([Spring AI](../spring-ai/10-agent-react), [LangChain4j](../langchain4j/10-agent-react))):

- Spring AI: `internalToolExecutionEnabled(false)` and drive `ToolCallingManager` yourself.
- LangChain4j: build the `ChatRequest` yourself and call `DefaultToolExecutor`.

Neither has a step limit by default. Both will loop as long as the model keeps asking.

### RAG

Spring AI: `QuestionAnswerAdvisor` for the simple case, `RetrievalAugmentationAdvisor` for the real
one. The advanced pipeline is a builder with named stages ([recipe 09](../spring-ai/09-rag-advanced)).

LangChain4j: `EmbeddingStoreContentRetriever` for the simple case, `DefaultRetrievalAugmentor` for
the real one. More parts are exposed - query transformers, content aggregators, injectors - and more
of them are `-beta` modules.

Both support pgvector with roughly the same amount of code (recipe 08 ([Spring AI](../spring-ai/08-rag-pgvector), [LangChain4j](../langchain4j/08-rag-pgvector))). Neither one makes ingestion
idempotent for you, which is the part that actually bites.

### Observability

Spring AI emits `gen_ai.client.operation` observations out of the box; add actuator and a tracing
backend and you are done ([recipe 16](../spring-ai/16-observability)).

LangChain4j has `ChatModelListener`, which is three methods and no framework. You get exactly what
you write, including the Micrometer wiring.

### MCP

Spring AI does both sides: `spring-ai-starter-mcp-client-*` and `spring-ai-starter-mcp-server-*`
([12](../spring-ai/12-mcp-client), [13](../spring-ai/13-mcp-server)). The server is a `ToolCallbackProvider` bean.

LangChain4j is a client only. [Recipe 13](../langchain4j/13-mcp-server) on the LangChain4j side uses the official MCP Java SDK with
an embedded Jetty, and writes the tool schemas by hand - which is worth seeing once, but it is more
code for the same result.

Both pairs speak streamable HTTP. Spring AI still defaults to the older SSE transport
(`spring.ai.mcp.server.protocol`), and LangChain4j has dropped it from the client side entirely, so
the default is the wrong one to leave in place.

### Evals

Spring AI ships `RelevancyEvaluator` and `FactCheckingEvaluator` ([recipe 17](../spring-ai/17-evals)).

LangChain4j ships nothing, and it barely matters: a judge is one prompt and one return type, and
writing it yourself means you own the judge prompt, which you want to own anyway.

## Release cadence

Spring AI moves with the Spring release train. LangChain4j moves faster, and a meaningful share of
its surface lives in `-beta` modules (`langchain4j-mcp`, `langchain4j-pgvector`,
`langchain4j-easy-rag`). Pin exact versions either way; assume LangChain4j will move under you more
often between minor releases.

## Choosing

Pick **Spring AI** if the application is already Spring Boot, if you want provider switching to be
an ops concern, or if tracing and metrics need to look like the rest of your stack with no work.

Pick **LangChain4j** if you are not on Spring, if you want the retrieval pipeline in pieces you can
replace, or if you would rather read 50 lines of construction than debug auto-configuration.

Pick either one if you are writing a prototype. Both take about a day to learn, and the concepts -
prompts, tools, retrieval, memory, evals - transfer completely. That is what these recipes are
really for.
