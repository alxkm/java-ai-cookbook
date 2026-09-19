# 17 - Evals (Spring AI)

**What:** a regression suite for a prompt. Three questions with reference answers, two judges -
relevancy and fact checking - and a pass rate at the end.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

Each case prints the answer and both verdicts. Change the system prompt in
`EvalsApplication.java` and watch the pass rate move - that is the whole point of the exercise.

## Where to look

- [`PromptSuite.java`](src/main/java/com/example/cookbook/PromptSuite.java) - the harness. Relevancy and fact checking fail for different reasons, which
  is why both are worth running.
- [`EvalsApplication.java`](src/main/java/com/example/cookbook/EvalsApplication.java) - the judge prompt is written out rather than inherited. Keep it narrow.
- [`PromptSuiteTest.java`](src/test/java/com/example/cookbook/PromptSuiteTest.java) - stubbed judges, so the harness is verified without any model call.

## Gotchas

- Give the judge its own `ChatClient`. A judge that inherits the assistant's system prompt agrees
  with the assistant.
- Every case costs three model calls: one answer plus two judgements. Run this on a schedule, not
  on every commit.
- Judges are not deterministic. Treat a pass-rate drop as a signal to look, not as a red build -
  or run each case several times and compare rates.
- A judge asked to score "quality" invents a scale. One yes/no question per judge is reproducible.
- Three cases is a demo. A suite worth trusting has the twenty questions your users actually ask,
  including the ones with no answer.

---

[All recipes](../../README.md) | [Same recipe in LangChain4j](../../langchain4j/17-evals) | [Previous: 16 Observability](../16-observability) | [Next: 18 Local models](../18-ollama)
