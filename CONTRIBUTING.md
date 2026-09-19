# Contributing

Recipes are welcome. Keep them small, keep them runnable, keep them tested.

## What makes a good recipe

One pattern, shown once, with nothing else in the way. If a reader has to understand two new things
to follow the example, it is two recipes.

- **Runnable in under a minute**, with one command and one environment variable.
- **Independent.** Its own `pom.xml`, its own wrapper, no parent project, no shared module. Copying
  the folder out of this repo has to work.
- **Tested without an API key.** Stub the model. What you are testing is your code - the prompt that
  gets assembled, the chunk that gets retrieved, the tool that gets called.
- **Honest about the sharp edges.** The Gotchas section is the part people come back for.

Duplication between recipes is deliberate. `Models.java`, `OrderTools.java` and
`KeywordEmbeddingModel.java` appear in several projects on purpose: a reader opens one folder, and
everything they need is in it.

## Recipe layout

```
<framework>/<nn>-<name>/
├── README.md
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/
└── src/
    ├── main/java/com/example/cookbook/
    ├── main/resources/          (application.yml for Spring AI)
    └── test/java/com/example/cookbook/
```

Number the recipe to match the same pattern in the other framework. If you add
`spring-ai/19-something`, `langchain4j/19-something` should follow, even if it lands in a separate
pull request. Add the row to the table in the root `README.md`.

## README template

Every recipe README has the same four sections, in this order:

```markdown
# <nn> - <Pattern> (<Framework>)

**What:** one or two sentences. What this shows, and what it deliberately leaves out.

## Run

​```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run            # or: ./mvnw -q compile exec:java
​```

## Where to look

- `File.java` - the two or three lines that matter, and why.

## Gotchas

- The thing that will cost someone an afternoon.
```

Write the Gotchas from something you actually hit. "Remember to set the API key" is not a gotcha;
"compile with `-parameters` or every tool argument arrives as null" is.

## Conventions

- **Java 21**, Maven, the wrapper committed alongside each recipe.
- **Package** `com.example.cookbook` everywhere. The projects are separate, so there is no clash.
- **Pinned versions.** Exact versions in the pom, exact model names in config. No version ranges,
  no `latest`.
- **Comments explain why**, never what. If a line needs a comment to say what it does, rewrite the
  line.
- **English** in code, comments, commit messages, issues and pull requests.
- **Four spaces**, 120 column limit, standard Java naming.

## Before you open a pull request

```bash
cd <framework>/<nn>-<name>
./mvnw clean test

bash tools/test_all.sh          # or all 36 at once, one line each
```

Tests must pass with no API key set and no network. If a test genuinely needs Docker (recipe 08) it
must skip itself when Docker is absent, not fail.

A recipe may also carry a `LiveModelTest` that does call a real provider. Gate it with
`@EnabledIfEnvironmentVariable(named = "COOKBOOK_LIVE", matches = "true")` so it is skipped by
default and in CI, and assert only on wiring - a successful call, usage metadata, the system prompt
arriving. Never assert on what the model knows: that test passes today and fails next week for no
reason, and checking answer quality is what the eval suite in recipe 17 is for.

```bash
COOKBOOK_LIVE=true AI_PROVIDER=ollama ./mvnw test
```

Check that the recipe still runs for real against at least one provider, and say which one you
tested in the pull request description.

If you add a recipe, mark its wrapper executable in the index or the Linux clone of your work
will not run:

```bash
git add -A
bash tools/fix_exec_bits.sh
```

On Windows git cannot read an executable bit, so `mvnw` goes in as a plain file and `./mvnw`
fails with "Permission denied" on every other platform. It looks fine locally, which is what makes
it easy to miss.

The tests stub the model so they run offline, which means they cannot catch a recipe that starts,
calls a provider and behaves wrongly. Run it for real as well:

```bash
bash tools/run_all_live.sh       # every recipe against Ollama, no keys needed
```

Two checks worth running before you push:

```bash
python tools/check_links.py         # every relative markdown link resolves; also runs in CI
python tools/check_config_keys.py   # every Spring property key exists in the dependency metadata
```

The second one needs the jars in `~/.m2`, so build first. It exists because Spring silently
ignores a property it does not recognise: a renamed or mistyped key costs nothing at startup and
quietly does nothing at runtime.

## Adding a provider or a version bump

Model and library versions are pinned in every project separately, which is tedious on purpose - it
means one recipe can move ahead without breaking the other seventeen. When bumping, do it in its own
commit and say in the message what changed in the API, if anything did.
