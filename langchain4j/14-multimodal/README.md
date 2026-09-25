# 14 - Multimodal (LangChain4j)

**What:** send a PNG and a question in the same user message, get a description back.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw -q compile exec:java
./mvnw -q compile exec:java -Dexec.args="what are the approximate values of each bar"
```

The image is `src/main/resources/images/chart.png` - a four-bar chart. Swap it for your own.

## Where to look

- [`Multimodal.java`](src/main/java/com/example/cookbook/Multimodal.java) - `question(...)` builds `UserMessage.from(TextContent, ImageContent)`; the
  image is base64, inlined in the request, and `mimeType(...)` reads its type off the first bytes.
- [`MultimodalTest.java`](src/test/java/com/example/cookbook/MultimodalTest.java) - goes through the recipe's own `question(...)`, and checks the image
  arrives byte for byte rather than merely "not blank".

## Gotchas

- The model has to support vision. `gpt-4o-mini` and `claude-sonnet-5` do; plain `llama3.2` does
  not, so the Ollama branch of `Models` defaults to `llava` (`ollama pull llava`, about 4.7 GB).
  Override it with `OLLAMA_MODEL` if you would rather use something smaller, such as `moondream`.
- `ImageContent.from(url)` also exists, but the provider then fetches the URL itself - it has to be
  publicly reachable. Base64 avoids that and is the safer default.
- Images are billed as tokens. Downscale before sending.
- The mime type goes with the image and the provider takes it at its word, so read it off the
  file's first bytes. A hardcoded `image/png` holds until the first JPEG someone drops in.
- Charts are read approximately. Do not use a vision model as an OCR or a data extractor without
  checking the numbers.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/14-multimodal) | [Previous: 13 MCP server](../13-mcp-server) | [Next: 15 Guardrails](../15-guardrails)
