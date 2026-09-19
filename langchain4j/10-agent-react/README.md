# 10 - Agent, ReAct loop (LangChain4j)

**What:** the same tools as recipe 04, but the loop is written out instead of hidden. Model asks
for a tool, the tool runs, the result goes back, repeat - with a step limit and a printed trace.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw -q compile exec:java
./mvnw -q compile exec:java -Dexec.args="is A-1003 arriving this week"
```

## Where to look

- [`AgentLoop.java`](src/main/java/com/example/cookbook/AgentLoop.java) - `ToolSpecifications.toolSpecificationsFrom(...)` to describe the tools,
  `DefaultToolExecutor` to run them, and a plain while loop over `toolExecutionRequests()`.
- [`AgentLoopTest.java`](src/test/java/com/example/cookbook/AgentLoopTest.java) - a scripted model drives a full multi-step run, and a model that never
  stops proves the step limit works.

## Gotchas

- Own the step limit. A model that keeps asking for the same tool is not rare.
- Every step resends the whole message list. Step six costs a lot more than step one.
- The assistant message with the tool requests has to go into the history *before* the
  `ToolExecutionResultMessage`, or the provider rejects the next request.
- If you only need "call tools until you get an answer", use `AiServices` from recipe 04. Write the
  loop when you need the trace, the limit, or a stopping rule of your own.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/10-agent-react) | [Previous: 09 RAG, advanced](../09-rag-advanced) | [Next: 11 Multi-agent](../11-multi-agent)
