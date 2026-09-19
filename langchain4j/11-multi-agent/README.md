# 11 - Multi-agent (LangChain4j)

**What:** a router and three specialist desks. The router returns a typed decision, the hand-off is
ordinary Java, and each desk has its own system prompt and its own tools.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw -q compile exec:java
./mvnw -q compile exec:java -Dexec.args="I want a refund for order A-1003"
```

## Where to look

- [`SupportDesk.java`](src/main/java/com/example/cookbook/SupportDesk.java) - one `AiServices` interface per desk, and a `Router` whose return type is a
  record with an enum. The switch over the enum is the whole hand-off.
- [`SupportDeskTest.java`](src/test/java/com/example/cookbook/SupportDeskTest.java) - asserts the route, the worker's system prompt, and that only the orders
  desk advertises tools.

## Gotchas

- Two model calls per message. Routing is not free - for two desks an `if` on a keyword may be the
  better engineering call.
- Make the router decide and nothing else. A router that also answers will sometimes answer instead
  of routing, and you lose the trace.
- Give each desk only the tools it needs. Handing every tool to every agent is how you get an
  orders agent issuing refunds.
- Enum values come back by name. Rename a constant and old logs stop matching the new code.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/11-multi-agent) | [Previous: 10 Agent, ReAct loop](../10-agent-react) | [Next: 12 MCP client](../12-mcp-client)
