# 15 - Guardrails (Spring AI)

**What:** one advisor doing the three checks that belong at the boundary - refuse prompt injection,
redact PII on the way in, refuse an answer that leaked the system prompt or PII on the way out.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

Three messages run: an ordinary question, one containing an email address, and a prompt injection
attempt. The guardrail prints what it did.

## Where to look

- [`GuardrailAdvisor.java`](src/main/java/com/example/cookbook/GuardrailAdvisor.java) - an advisor that blocks simply never calls `chain.nextCall(...)`.
  Nothing reaches the provider and nothing is billed.
- [`Pii.java`](src/main/java/com/example/cookbook/Pii.java) - regex redaction, deliberately small.
- [`GuardrailAdvisorTest.java`](src/test/java/com/example/cookbook/GuardrailAdvisorTest.java) - four cases, no API key: pass through, redact, block input,
  block output.

## Gotchas

- Regex PII detection is the cheap layer, not the whole answer. It catches the obvious cases before
  the data leaves your network; it will miss names, addresses and anything unusual.
- Injection patterns are a blocklist, and blocklists are worked around. Treat this as friction,
  not as a control. The real control is not giving the model anything dangerous to do.
- The canary marker in the system prompt is the cheapest leak detector there is. One unique string,
  checked on every answer.
- `getOrder()` decides where the advisor sits in the chain. Guardrails belong at the outside of it.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/15-guardrails) | [Previous: 14 Multimodal](../14-multimodal) | [Next: 16 Observability](../16-observability)
