# 13 - MCP server (Spring AI)

**What:** the `@Tool` methods from recipe 04, published over MCP so any client can use them -
recipe 12, an IDE, a desktop assistant.

## Run

```bash
./mvnw spring-boot:run
# streamable HTTP endpoint on http://localhost:8081/mcp
```

No API key is needed. A server has no model in it.

## Test

```bash
./mvnw test
```

The test starts the server and connects a real MCP client to it, then lists and calls tools.

## Where to look

- [`McpServerApplication.java`](src/main/java/com/example/cookbook/McpServerApplication.java) - one `ToolCallbackProvider` bean. That is the entire server.
- [`OrderTools.java`](src/main/java/com/example/cookbook/OrderTools.java) - unchanged from recipe 04. The same annotations serve both in-process calls
  and MCP.
- [`application.yml`](src/main/resources/application.yml) - `protocol: STREAMABLE`, plus the server
  name, version and instructions, which is what clients see first.

## Gotchas

- Tool descriptions are now a public API. Clients you have never seen will read them, and the
  model on the other end decides what to call based on that text alone.
- Exposing a tool exposes whatever it can do. Scope the methods, validate the arguments, and do
  not publish anything you would not put behind an unauthenticated HTTP endpoint.
- `protocol` defaults to `sse`, the older two-endpoint transport. Current clients ask for
  streamable HTTP; leaving the default in place is a connection failure that looks like a
  networking problem.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/13-mcp-server) | [Previous: 12 MCP client](../12-mcp-client) | [Next: 14 Multimodal](../14-multimodal)
