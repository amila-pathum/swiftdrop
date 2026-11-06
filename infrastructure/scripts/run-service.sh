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
	# Variables already set in the environment win over .env, so one run can
	# override a value without editing it, for example:
	#   SPRING_PROFILES_ACTIVE=rds run-service.sh identity-service
	preset=()
	for name in $(compgen -e); do
		preset+=("$name=${!name}")
	done
	set -a
	# shellcheck source=/dev/null
	source "$root/.env"
	set +a
	for entry in "${preset[@]}"; do
		name="${entry%%=*}"
		[[ "${!name-}" == "${entry#*=}" ]] || export "$entry"
	done
else
	echo "Warning: $root/.env not found; copy .env.example to .env first." >&2
fi

cd "$dir"
exec ./mvnw spring-boot:run
