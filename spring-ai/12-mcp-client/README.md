# 12 - MCP client (Spring AI)

**What:** an assistant whose tools come from a separate process. Nothing in this project names a
tool - it discovers them over MCP and hands them to the model.

## Run

Start a server first. Recipe 13 in this repo is one:

```bash
cd ../13-mcp-server && ./mvnw spring-boot:run     # terminal 1

cd ../12-mcp-client                               # terminal 2
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

Any other MCP server speaking streamable HTTP works too:

```bash
MCP_SERVER_URL=http://localhost:9000 ./mvnw spring-boot:run
```

## Where to look

- [`application.yml`](src/main/resources/application.yml) - `spring.ai.mcp.client.streamable-http.connections`
  is the whole configuration.
- [`McpAssistant.java`](src/main/java/com/example/cookbook/McpAssistant.java) - takes a `ToolCallbackProvider` and passes the callbacks to the model.
  There is no MCP-specific code in it.
- [`McpAssistantTest.java`](src/test/java/com/example/cookbook/McpAssistantTest.java) - checks that externally supplied tools reach the model. The protocol
  itself is covered by recipe 13's test, which runs a real client against a real server.

## Gotchas

- The client connects at startup. If the server is down, the app fails to start - decide whether
  that is what you want before you ship it.
- Tool names come from the server and can collide across servers. Spring AI prefixes them with the
  connection name for that reason.
- There is also an `sse` block for the older two-endpoint transport. Pick the one your server
  actually speaks; the failure mode for the wrong choice looks like a network problem.
- A remote tool is a remote call: it can be slow, fail, or return something unexpected, and the
  model will pass whatever comes back straight into its answer.
- Never connect to an MCP server you do not trust. Tool descriptions are prompt text, and they
  are written by whoever runs the server.
- Tool calling engages only if the model's `getOptions()` returns a `ToolCallingChatOptions`.
  `ToolCallingAdvisor` passes the request through untouched otherwise, which is how a model
  that cannot call tools opts out - and also how a hand-written test double silently drops
  every tool while still returning an answer. `getDefaultOptions()` is deprecated in 2.0 and
  overriding it does nothing here.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/12-mcp-client) | [Previous: 11 Multi-agent](../11-multi-agent) | [Next: 13 MCP server](../13-mcp-server)
