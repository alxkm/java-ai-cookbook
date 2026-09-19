# 14 - Multimodal (Spring AI)

**What:** send a PNG and a question in the same user message, get a description back.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
./mvnw spring-boot:run -Dspring-boot.run.arguments="what are the approximate values of each bar"
```

The image is `src/main/resources/images/chart.png` - a four-bar chart. Swap it for your own.

## Where to look

- [`MultimodalApplication.java`](src/main/java/com/example/cookbook/MultimodalApplication.java) - `.user(u -> u.text(...).media(MimeTypeUtils.IMAGE_PNG, resource))`.
  One message, two parts.
- [`MultimodalTest.java`](src/test/java/com/example/cookbook/MultimodalTest.java) - asserts the media really is attached, without calling a model.

## Gotchas

- The model has to support vision. `gpt-4o-mini` and `claude-sonnet-5` do; plain `llama3.2` does not,
  so the Ollama profile here uses `llava` (`ollama pull llava`, about 4.7 GB). Override it with
  `OLLAMA_CHAT_MODEL` if you would rather use something smaller, such as `moondream`.
- Images are billed as tokens, and a large screenshot is expensive. Downscale before sending -
  most questions do not need full resolution.
- The mime type is not a formality. Send a JPEG labelled `image/png` and the provider rejects it.
- Charts are read approximately. Do not use a vision model as an OCR or a data extractor without
  checking the numbers.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/14-multimodal) | [Previous: 13 MCP server](../13-mcp-server) | [Next: 15 Guardrails](../15-guardrails)
