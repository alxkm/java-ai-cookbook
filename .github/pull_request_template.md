## What this changes

<!-- One or two sentences. If it is a new recipe, say which pattern and why it earns its own folder. -->

## Checklist

- [ ] `./mvnw clean test` passes in the recipe folder with no API key set
- [ ] Ran it for real against at least one provider - which one: <!-- openai / anthropic / ollama -->
- [ ] README follows the What / Run / Where to look / Gotchas shape
- [ ] Versions are pinned exactly, no ranges
- [ ] New recipe: row added to the table in the root README, footer links in the neighbouring recipes,
      an entry in `.github/dependabot.yml`, and `bash tools/fix_exec_bits.sh` run after `git add`
