#!/usr/bin/env bash
# Starts one backend service with the root .env loaded, for example:
#   infrastructure/scripts/run-service.sh identity-service
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
service="${1:?usage: run-service.sh <service>, for example identity-service}"
dir="$root/backend/$service"

if [[ ! -x "$dir/mvnw" ]]; then
	echo "Unknown service '$service'. Choose one of:" >&2
	ls "$root/backend" >&2
	exit 1
fi
if [[ -f "$root/.env" ]]; then
	set -a
	# shellcheck source=/dev/null
	source "$root/.env"
	set +a
else
	echo "Warning: $root/.env not found; copy .env.example to .env first." >&2
fi

cd "$dir"
exec ./mvnw spring-boot:run
