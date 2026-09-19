#!/usr/bin/env bash
#
# Run the test suite of every recipe and print one line each.
#
# The recipes are deliberately independent projects, so there is no aggregator pom and no
# `mvn test` at the root that covers everything. This is that command.
#
#     bash tools/test_all.sh                 # offline, no keys - what CI runs
#     COOKBOOK_LIVE=true AI_PROVIDER=ollama bash tools/test_all.sh
#
# Recipe 08 needs Docker; its tests skip themselves when Docker is not running.
# Exits non-zero if any recipe fails.
set -uo pipefail

cd "$(dirname "$0")/.."

failed=0
logs=$(mktemp -d)

for recipe in spring-ai/*/ langchain4j/*/; do
    recipe="${recipe%/}"
    [ -f "$recipe/pom.xml" ] || continue

    log="$logs/$(echo "$recipe" | tr '/' '_').log"
    printf '%-34s ' "$recipe"

    if (cd "$recipe" && ./mvnw -B -q clean test) > "$log" 2>&1; then
        echo "OK"
    else
        echo "FAILED"
        failed=$((failed + 1))
        sed -n 's/^\[ERROR\] *//p' "$log" | head -5 | sed 's/^/      /'
    fi
done

echo
if [ "$failed" -eq 0 ]; then
    echo "all recipes passed"
    rm -rf "$logs"
    exit 0
fi

echo "$failed recipe(s) failed - full output in $logs"
exit 1
