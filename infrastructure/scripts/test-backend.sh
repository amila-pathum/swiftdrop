#!/usr/bin/env bash
# Builds and tests every backend service, each with its own Maven wrapper.
# Database tests use Testcontainers and are skipped when Docker is not running.
set -uo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
failed=()

for dir in "$root"/backend/*/; do
	service="$(basename "$dir")"
	echo "==> $service"
	if ! (cd "$dir" && ./mvnw -q verify); then
		failed+=("$service")
	fi
done

if ((${#failed[@]})); then
	echo "Failed: ${failed[*]}" >&2
	exit 1
fi
echo "All backend services built and passed their tests."
