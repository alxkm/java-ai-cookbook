# 15 - Guardrails (LangChain4j)

**What:** three guardrails at the two seams that exist - refuse prompt injection, redact PII on the
way in, refuse an answer that leaked the system prompt or PII on the way out.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw -q compile exec:java
```

Three messages run: an ordinary question, one containing an email address, and a prompt injection
attempt. The guardrail prints what it did.

## Where to look

- [`Guards.java`](src/main/java/com/example/cookbook/Guards.java) - `fatal(...)` aborts the call, `successWith(...)` rewrites the message instead of
  rejecting it. Redaction needs the second one.
- [`Guardrails.java`](src/main/java/com/example/cookbook/Guardrails.java) - `@InputGuardrails` / `@OutputGuardrails` on the service interface; guardrails
  that need constructor arguments are registered on the builder instead.
- [`GuardrailsTest.java`](src/test/java/com/example/cookbook/GuardrailsTest.java) - four cases, no API key.

## Gotchas

- Guardrail classes are instantiated reflectively from the annotation, so they must be `public`
  with a public no-arg constructor. A package-private class fails at runtime, not at compile time.
- `failure(...)` asks the model to try again, `fatal(...)` gives up. Retrying a prompt injection
  is not useful; retrying a malformed JSON answer is.
- Regex PII detection is the cheap layer, not the whole answer.
- Injection patterns are a blocklist, and blocklists are worked around. Treat this as friction,
  not as a control. The real control is not giving the model anything dangerous to do.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/15-guardrails) | [Previous: 14 Multimodal](../14-multimodal) | [Next: 16 Observability](../16-observability)
