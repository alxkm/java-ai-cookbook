#!/usr/bin/env bash
#
# Mark every Maven wrapper as executable in the git index.
#
# Why this exists: on Windows git sets core.fileMode=false, because NTFS has no executable bit to
# read. Every mvnw then goes into the repository as mode 100644, and the first Linux or macOS
# clone - including the CI runner - fails on `./mvnw` with "Permission denied". The files look
# perfectly fine locally, which is what makes it hard to spot.
#
# Run this once after the first `git add`, and again whenever a new recipe is added:
#
#     git add -A
#     bash tools/fix_exec_bits.sh
#     git status            # the mvnw files should now show mode 100755
#
set -euo pipefail

cd "$(dirname "$0")/.."

changed=0
while IFS= read -r wrapper; do
    mode=$(git ls-files -s -- "$wrapper" | awk '{print $1}')
    if [ "$mode" != "100755" ]; then
        git update-index --chmod=+x -- "$wrapper"
        echo "  +x $wrapper"
        changed=$((changed + 1))
    fi
done < <(git ls-files -- '*/mvnw')

if [ "$changed" -eq 0 ]; then
    echo "every mvnw is already executable in the index"
else
    echo "$changed wrapper(s) marked executable"
fi
