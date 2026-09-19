# 11 - Multi-agent (Spring AI)

**What:** a router and three specialist desks. The router returns a typed decision, the hand-off is
ordinary Java, and each desk has its own system prompt and its own tools.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
./mvnw spring-boot:run -Dspring-boot.run.arguments="I want a refund for order A-1003"
```

Each message prints the desk it was routed to and why, before the answer.

## Where to look

- [`SupportDesk.java`](src/main/java/com/example/cookbook/SupportDesk.java) - `builder.build().mutate()` gives each desk its own configured `ChatClient`
  from one auto-configured builder. The routing decision is a record, not free text.
- [`SupportDeskTest.java`](src/test/java/com/example/cookbook/SupportDeskTest.java) - asserts the route and that the worker really gets its own system prompt.

## Gotchas

- Two model calls per message. Routing is not free - for two desks an `if` on a keyword may be
  the better engineering call.
- Make the router decide and nothing else. A router that also answers will sometimes answer instead
  of routing, and you lose the trace.
- Give each desk only the tools it needs. Handing every tool to every agent is how you get an
  orders agent issuing refunds.
- The reason field is worth its tokens. When routing goes wrong it is the only evidence you have.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/11-multi-agent) | [Previous: 10 Agent, ReAct loop](../10-agent-react) | [Next: 12 MCP client](../12-mcp-client)
