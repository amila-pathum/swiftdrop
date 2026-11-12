#!/usr/bin/env bash
# Starts one backend service. Run it from anywhere, one terminal per service.
#
# Local Docker PostgreSQL (the default), with the root .env loaded:
#   infrastructure/scripts/run-service.sh identity-service
#
# AWS RDS: activates the Spring profile "rds" and takes the database settings
# from the root .env.rds (copy .env.rds.example) instead of .env:
#   DB_PROFILE=rds infrastructure/scripts/run-service.sh identity-service
#
# Show which database and user would be used, without starting anything:
#   DRY_RUN=1 DB_PROFILE=rds infrastructure/scripts/run-service.sh identity-service
#
# Variables already exported in your shell win over both files, so one run can
# override a value without editing a file. Passwords are never printed.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
service="${1:?usage: [DB_PROFILE=local|rds] run-service.sh <service>, for example identity-service}"
dir="$root/backend/$service"
profile="${DB_PROFILE:-local}"

fail() {
	echo "run-service.sh: $*" >&2
	exit 1
}

if [[ ! -x "$dir/mvnw" ]]; then
	echo "Unknown service '$service'. Choose one of:" >&2
	ls "$root/backend" >&2
	exit 1
fi
case "$profile" in
local | rds) ;;
*) fail "DB_PROFILE must be 'local' (the default) or 'rds', not '$profile'." ;;
esac

# Prefix of each service's variables in .env.rds; the service connects to RDS
# with <PREFIX>_DB_NAME, <PREFIX>_DB_USERNAME and <PREFIX>_DB_PASSWORD.
rds_prefixes=(IDENTITY PARCEL MERCHANT COURIER DISPATCH PRICING NOTIFICATION)
rds_prefix() {
	case "$1" in
	identity-service) echo IDENTITY ;;
	parcel-service) echo PARCEL ;;
	merchant-service) echo MERCHANT ;;
	courier-service) echo COURIER ;;
	dispatch-service) echo DISPATCH ;;
	pricing-service) echo PRICING ;;
	notification-service) echo NOTIFICATION ;;
	*) return 1 ;;
	esac
}

# Everything exported before this script loads a file, restored after each
# file so that the shell always wins.
shell_env=()
for name in $(compgen -e); do
	shell_env+=("$name=${!name}")
done
restore_shell_env() {
	local entry name
	for entry in "${shell_env[@]}"; do
		name="${entry%%=*}"
		[[ -v "$name" && "${!name}" == "${entry#*=}" ]] || export "$entry"
	done
}
load_env_file() {
	set -a
	# shellcheck source=/dev/null
	source "$1"
	set +a
}
shell_has() {
	local entry
	for entry in "${shell_env[@]}"; do
		[[ "${entry%%=*}" == "$1" ]] && return 0
	done
	return 1
}

# .env provides everything else (JWT_SECRET, ports, Google sign-in) in both modes.
if [[ -f "$root/.env" ]]; then
	load_env_file "$root/.env"
else
	echo "Warning: $root/.env not found; copy .env.example to .env first." >&2
fi

if [[ "$profile" == local ]]; then
	restore_shell_env
	# The rds profile reads DB_NAME, DB_USERNAME and DB_PASSWORD, which in .env
	# are local values (DB_PASSWORD is the Identity Service's Docker password).
	if [[ ",${SPRING_PROFILES_ACTIVE-}," == *,rds,* ]]; then
		fail "SPRING_PROFILES_ACTIVE includes 'rds' but DB_PROFILE is not 'rds'. Run
  DB_PROFILE=rds infrastructure/scripts/run-service.sh $service
so the database settings come from .env.rds, and unset SPRING_PROFILES_ACTIVE."
	fi
	echo "run-service.sh: $service with the local Docker PostgreSQL (settings from .env)"
	[[ "${DRY_RUN:-}" == 1 ]] && exit 0
	cd "$dir"
	exec ./mvnw spring-boot:run
fi

# ---- DB_PROFILE=rds ----
prefix="$(rds_prefix "$service")" ||
	fail "'$service' has no AWS RDS database mapping. Services with an RDS database:
  identity-service parcel-service merchant-service courier-service dispatch-service pricing-service notification-service"

# Database values from .env must never reach RDS: there they are local Docker
# credentials (PARCEL_DB_PASSWORD and so on share their names with .env.rds).
unset DB_HOST DB_PORT DB_NAME DB_USERNAME DB_PASSWORD
for p in "${rds_prefixes[@]}"; do
	unset "${p}_DB_NAME" "${p}_DB_USERNAME" "${p}_DB_PASSWORD"
done
if [[ -f "$root/.env.rds" ]]; then
	load_env_file "$root/.env.rds"
else
	echo "Warning: $root/.env.rds not found; copy .env.rds.example to .env.rds and set the passwords." >&2
fi
restore_shell_env

# The generic variables are always derived from this service's own ones, so a
# DB_PASSWORD left in the shell (for example the Docker one) cannot be used.
for generic in DB_NAME DB_USERNAME DB_PASSWORD; do
	if shell_has "$generic"; then
		echo "Note: ignoring $generic from your shell; with DB_PROFILE=rds it comes from ${prefix}_${generic}." >&2
	fi
done

name_var="${prefix}_DB_NAME"
user_var="${prefix}_DB_USERNAME"
password_var="${prefix}_DB_PASSWORD"
missing=()
for var in DB_HOST "$name_var" "$user_var" "$password_var"; do
	[[ -n "${!var-}" ]] || missing+=("$var")
done
if ((${#missing[@]})); then
	fail "missing or empty for $service: ${missing[*]}
Set them in $root/.env.rds (cp .env.rds.example .env.rds) or export them."
fi
DB_PORT="${DB_PORT:-5432}"
[[ "$DB_PORT" =~ ^[0-9]+$ ]] || fail "DB_PORT must be a number, not '$DB_PORT'."

export DB_HOST DB_PORT
export DB_NAME="${!name_var}" DB_USERNAME="${!user_var}" DB_PASSWORD="${!password_var}"
# Only this service's credentials reach the Java process.
for p in "${rds_prefixes[@]}"; do
	[[ "$p" == "$prefix" ]] || unset "${p}_DB_NAME" "${p}_DB_USERNAME" "${p}_DB_PASSWORD"
done
if [[ -z "${SPRING_PROFILES_ACTIVE-}" ]]; then
	SPRING_PROFILES_ACTIVE=rds
elif [[ ",$SPRING_PROFILES_ACTIVE," != *,rds,* ]]; then
	SPRING_PROFILES_ACTIVE="$SPRING_PROFILES_ACTIVE,rds"
fi
export SPRING_PROFILES_ACTIVE

# Each service owns exactly one database; catch a copy-paste mix-up in .env.rds.
expected_database="${service%-service}_db"
[[ "$DB_NAME" == "$expected_database" ]] ||
	fail "$name_var is '$DB_NAME', but $service must use its own database '$expected_database'."

cat <<EOF
run-service.sh: $service against AWS RDS (Spring profile '$SPRING_PROFILES_ACTIVE')
  host:     $DB_HOST
  port:     $DB_PORT
  database: $DB_NAME
  user:     $DB_USERNAME
  password: set from $password_var (not shown)
EOF
[[ "${DRY_RUN:-}" == 1 ]] && exit 0
cd "$dir"
exec ./mvnw spring-boot:run
