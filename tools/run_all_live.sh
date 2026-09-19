#!/usr/bin/env bash
#
# Actually run every recipe against a real provider and report what happened.
#
# This is not the test suite. The tests stub the model so they can run offline, and that is the
# right trade - but it means they cannot see a recipe that starts, calls a provider and behaves
# wrongly. Four of the bugs in this repo were found by running the recipes, not by testing them:
# an agent trace printing empty tool results, a collection return type that throws on models
# without native JSON schema, a query transformer that will not compose with a re-ranker, and a
# splitter that turned the whole corpus into one chunk.
#
#     ollama serve
#     bash tools/run_all_live.sh                      # uses Ollama, no keys needed
#     AI_PROVIDER=openai OPENAI_API_KEY=sk-... bash tools/run_all_live.sh
#
# Expect this to take a while on a local model, and expect to read the logs rather than trust the
# OK column: a recipe can exit zero and still answer nonsense.
set -uo pipefail

cd "$(dirname "$0")/.."

: "${AI_PROVIDER:=ollama}"
: "${EMBEDDING_PROVIDER:=$AI_PROVIDER}"
export AI_PROVIDER EMBEDDING_PROVIDER

LOGS="${LOGS_DIR:-$(mktemp -d)}"
mkdir -p "$LOGS"
# 900s because spring-ai/09 rewrites every query with an extra model call, and on a local
# 3B model three questions take well over ten minutes.
TIMEOUT="${RECIPE_TIMEOUT:-900}"

# Recipes needing something extra are skipped: 08 wants a database, 12 wants the server from 13,
# 13 is a server with no model, 14 wants a vision model.
SKIP="08-rag-pgvector 12-mcp-client 13-mcp-server 14-multimodal"

run() {
    local recipe="$1" limit="$2"
    shift 2
    local log="$LOGS/$(echo "$recipe" | tr '/' '_').log"
    printf '%-34s ' "$recipe"

    ( cd "$recipe" && timeout "$limit" "$@" ) > "$log" 2>&1
    local rc=$?

    # Order matters. A recipe killed by the timer makes the Spring Boot plugin print
    # "Failed to execute goal ... exit code: 143", which reads exactly like a real failure -
    # so the timeout has to be ruled out first, or every long or long-running recipe lies.
    if [ "$rc" -eq 124 ]; then
        echo "OK (still running when the timer ran out - raise RECIPE_TIMEOUT to see it finish)"
    elif grep -qE "BUILD FAILURE|Application run failed|Failed to execute goal" "$log"; then
        echo "FAILED"
    elif [ "$rc" -ne 0 ]; then
        echo "FAILED (rc=$rc)"
    else
        echo "OK"
    fi
}

skipped() {
    case " $SKIP " in *" $1 "*) return 0 ;; *) return 1 ;; esac
}

for recipe in spring-ai/*/ langchain4j/*/; do
    recipe="${recipe%/}"
    name="${recipe##*/}"
    [ -f "$recipe/pom.xml" ] || continue

    if skipped "$name"; then
        printf '%-34s %s\n' "$recipe" "skipped (needs a database, a server or a vision model)"
        continue
    fi

    # 02 starts an SSE endpoint and never returns; give it a short leash
    limit="$TIMEOUT"
    [ "$name" = "02-streaming" ] && limit=120

    case "$recipe" in
        spring-ai/*)   run "$recipe" "$limit" ./mvnw -B -q spring-boot:run ;;
        langchain4j/*) run "$recipe" "$limit" ./mvnw -B -q compile exec:java ;;
    esac
done

echo
echo "logs: $LOGS"
