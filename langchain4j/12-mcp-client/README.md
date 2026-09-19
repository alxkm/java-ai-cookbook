# 12 - MCP client (LangChain4j)

**What:** an assistant whose tools come from a separate process. Nothing in this project names a
tool - it discovers them over MCP and hands them to the model.

## Run

Start a server first. Recipe 13 in this repo is one:

```bash
cd ../13-mcp-server && ./mvnw -q compile exec:java    # terminal 1

cd ../12-mcp-client                                   # terminal 2
export OPENAI_API_KEY=sk-...
./mvnw -q compile exec:java
```

Any other MCP server speaking streamable HTTP works too:

```bash
MCP_SERVER_URL=http://localhost:9000/mcp ./mvnw -q compile exec:java
```

## Where to look

- [`McpClientMain.java`](src/main/java/com/example/cookbook/McpClientMain.java) - `StreamableHttpMcpTransport` -> `DefaultMcpClient` -> `McpToolProvider`.
  Three objects, and the third one is just a `ToolProvider`.
- [`McpAssistant.java`](src/main/java/com/example/cookbook/McpAssistant.java) - takes a `ToolProvider`. There is no MCP-specific code in it, which is the
  point: `ToolProvider` is the seam.
- [`McpAssistantTest.java`](src/test/java/com/example/cookbook/McpAssistantTest.java) - checks that externally supplied tools are advertised and executed. The
  protocol itself is covered by recipe 13's test, which runs a real client against a real server.

## Gotchas

- `McpClient` is `AutoCloseable` and holds a live connection. Leaking it leaks threads.
- LangChain4j dropped the plain SSE transport; `StreamableHttpMcpTransport` is what current servers
  expect. An old SSE-only server will not connect.
- Tool names come from the server and can collide across servers. Use `toolNameMapper` on
  `McpToolProvider` when you connect to more than one.
- Never connect to an MCP server you do not trust. Tool descriptions are prompt text, written by
  whoever runs the server.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/12-mcp-client) | [Previous: 11 Multi-agent](../11-multi-agent) | [Next: 13 MCP server](../13-mcp-server)
