# 17 - Evals (LangChain4j)

**What:** a regression suite for a prompt. Three questions with reference answers, an LLM judge
with a typed verdict, and a pass rate at the end.

## Run

```bash
export OPENAI_API_KEY=sk-...
./mvnw -q compile exec:java
```

Each case prints the answer and the judge's reason. Change the `@SystemMessage` in `Evals.java`
and watch the pass rate move - that is the whole point of the exercise.

## Where to look

- [`Judge.java`](src/main/java/com/example/cookbook/Judge.java) - LangChain4j has no built-in evaluator, and it turns out not to need one: a judge
  is one prompt and one return type.
- [`PromptSuite.java`](src/main/java/com/example/cookbook/PromptSuite.java) - the harness takes a `Function<String, String>`, so anything that answers a
  question can be evaluated, not just an AiService.
- [`PromptSuiteTest.java`](src/test/java/com/example/cookbook/PromptSuiteTest.java) - a stubbed judge, so the harness is verified without any model call.

## Gotchas

- Give the judge a clean service. A judge that inherits the assistant's system prompt agrees with
  the assistant.
- Every case costs two model calls. Run this on a schedule, not on every commit.
- Judges are not deterministic. Treat a pass-rate drop as a signal to look, not as a red build -
  or run each case several times and compare rates.
- A judge asked to score "quality" invents a scale. One yes/no question per judge is reproducible.
- Three cases is a demo. A suite worth trusting has the twenty questions your users actually ask,
  including the ones with no answer.

---

[All recipes](../../README.md) | [Same recipe in Spring AI](../../spring-ai/17-evals) | [Previous: 16 Observability](../16-observability) | [Next: 18 Local models](../18-ollama)
