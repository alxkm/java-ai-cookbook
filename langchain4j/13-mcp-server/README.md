# 13 - MCP server (LangChain4j)

**What:** an MCP server with no framework behind it - three tools, a hand-written JSON schema for
each, and an embedded Jetty.

LangChain4j is a client library: it consumes MCP servers (recipe 12) but does not host them. The
official MCP Java SDK does, and this is what that looks like.

## Run

```bash
./mvnw -q compile exec:java
# streamable HTTP endpoint on http://localhost:8081/mcp

./mvnw -q compile exec:java -Dexec.args="9000"   # different port
```

No API key is needed. A server has no model in it.

## Test

```bash
./mvnw test
```

The test starts the server and connects a real MCP client to it, then lists and calls tools.

## Where to look

- [`OrderMcpServer.java`](src/main/java/com/example/cookbook/OrderMcpServer.java) - `McpServer.sync(transport).toolCall(tool, handler)`. The `tool(...)`
  helper builds the input schema by hand, which makes it obvious what the `@Tool` annotations in
  recipe 04 generate for you.
- [`OrderService.java`](src/main/java/com/example/cookbook/OrderService.java) - plain domain code that knows nothing about MCP.

## Gotchas

- Tool descriptions are now a public API. Clients you have never seen will read them, and the model
  on the other end decides what to call based on that text alone.
- Exposing a tool exposes whatever it can do. Scope the methods, validate the arguments, and do not
  publish anything you would not put behind an unauthenticated HTTP endpoint.
- The input schema is the contract. `required` is what stops a client from calling `orderStatus`
  with no arguments at all.
- Streamable HTTP is one endpoint (`/mcp`). The older two-endpoint SSE transport still exists in
  the SDK, but current clients do not ask for it.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/13-mcp-server) | [Previous: 12 MCP client](../12-mcp-client) | [Next: 14 Multimodal](../14-multimodal)
