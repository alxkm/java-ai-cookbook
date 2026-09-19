# 10 - Agent, ReAct loop (Spring AI)

**What:** the same tools as recipe 04, but the loop is written out instead of hidden. Model asks
for a tool, the tool runs, the result goes back, repeat - with a step limit and a printed trace.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
./mvnw spring-boot:run -Dspring-boot.run.arguments="is A-1003 arriving this week"
```

The default question needs several tool calls, so the trace is worth reading.

## Where to look

- [`AgentLoop.java`](src/main/java/com/example/cookbook/AgentLoop.java) - `internalToolExecutionEnabled(false)` is the switch that hands the loop back
  to you. Everything else is a while loop over `response.hasToolCalls()`.
- [`AgentLoopTest.java`](src/test/java/com/example/cookbook/AgentLoopTest.java) - a scripted model drives a full multi-step run, and a model that never
  stops proves the step limit works.

## Gotchas

- Own the step limit. A model that keeps asking for the same tool is not rare, and the default
  loop has no reason to stop.
- Every step resends the whole conversation. Step six costs a lot more than step one - the trace
  is also a cost report.
- `executeToolCalls` returns the new conversation history; build the next `Prompt` from it rather
  than appending messages yourself.
- If you only need "call tools until you get an answer", use `ChatClient.tools(...)` from recipe 04.
  Write the loop when you need the trace, the limit, or a stopping rule of your own.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/10-agent-react) | [Previous: 09 RAG, advanced](../09-rag-advanced) | [Next: 11 Multi-agent](../11-multi-agent)
