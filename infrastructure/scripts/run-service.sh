#!/usr/bin/env bash
# Starts one backend service. Run it from anywhere, one terminal per service.
#
# Local Docker PostgreSQL (the default), with the root .env loaded:
#   infrastructure/scripts/run-service.sh identity-service
#
# AWS RDS: activates the Spring profile "rds" and takes the database settings
# from the RDS_* variables in the root .env (see .env.example) instead of the
# local Docker ones:
#   DB_PROFILE=rds infrastructure/scripts/run-service.sh identity-service
#
# Show which database and user would be used, without starting anything:
#   DRY_RUN=1 DB_PROFILE=rds infrastructure/scripts/run-service.sh identity-service
#
# Variables already exported in your shell win over .env, so one run can
# override a value without editing a file. Passwords are never printed.
# An old separate .env.rds file (un-prefixed names) is still read when .env has
# no RDS_DB_HOST, so teammates can move their passwords over in their own time.
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

# Prefix of each service's RDS variables; the service connects to RDS with
# RDS_<PREFIX>_DB_NAME, RDS_<PREFIX>_DB_USERNAME and RDS_<PREFIX>_DB_PASSWORD from
# .env (a legacy .env.rds uses the same names without the RDS_ prefix).
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

# Extra JVM options for notification-service only, from .env, e.g. a truststore
# that trusts a local HTTPS webhook receiver such as StyleCart's
# (stylecart/backend/target/local-tls/swiftdrop-env.sh prints the value).
# A JAVA_TOOL_OPTIONS exported in your shell still wins.
apply_service_jvm_options() {
	if [[ "$service" == notification-service && -n "${NOTIFICATION_JAVA_TOOL_OPTIONS:-}" && -z "${JAVA_TOOL_OPTIONS:-}" ]]; then
		export JAVA_TOOL_OPTIONS="$NOTIFICATION_JAVA_TOOL_OPTIONS"
		echo "run-service.sh: JAVA_TOOL_OPTIONS set from NOTIFICATION_JAVA_TOOL_OPTIONS"
	fi
	unset NOTIFICATION_JAVA_TOOL_OPTIONS
}

validate_jwt_secret() {
    [[ -n "${JWT_SECRET:-}" ]] || fail "JWT_SECRET must be configured in $root/.env before starting services."
    local key_bytes
    key_bytes="$(printf '%s' "$JWT_SECRET" | base64 --decode 2>/dev/null | wc -c)" ||
        fail "JWT_SECRET must be valid Base64."
    ((key_bytes >= 32)) || fail "JWT_SECRET must contain at least 256 bits (32 decoded bytes)."
}

# .env provides everything else (JWT_SECRET, ports, Google sign-in) in both modes.
if [[ -f "$root/.env" ]]; then
	load_env_file "$root/.env"
else
	echo "Warning: $root/.env not found; copy .env.example to .env first." >&2
fi

if [[ "$profile" == local ]]; then
	restore_shell_env
    validate_jwt_secret
	# The rds profile reads DB_NAME, DB_USERNAME and DB_PASSWORD, which in .env
	# are local values (DB_PASSWORD is the Identity Service's Docker password).
	if [[ ",${SPRING_PROFILES_ACTIVE-}," == *,rds,* ]]; then
		fail "SPRING_PROFILES_ACTIVE includes 'rds' but DB_PROFILE is not 'rds'. Run
  DB_PROFILE=rds infrastructure/scripts/run-service.sh $service
so the database settings come from the RDS_* variables, and unset SPRING_PROFILES_ACTIVE."
	fi
	# RDS credentials never reach a service running against the local database.
	for name in $(compgen -e | grep '^RDS_' || true); do unset "$name"; done
	echo "run-service.sh: $service with the local Docker PostgreSQL (settings from .env)"
	[[ "${DRY_RUN:-}" == 1 ]] && exit 0
	apply_service_jvm_options
	cd "$dir"
	exec ./mvnw spring-boot:run
fi

# ---- DB_PROFILE=rds ----
prefix="$(rds_prefix "$service")" ||
	fail "'$service' has no AWS RDS database mapping. Services with an RDS database:
  identity-service parcel-service merchant-service courier-service dispatch-service pricing-service notification-service"

# The un-prefixed database values in .env are the local Docker credentials
# (PARCEL_DB_PASSWORD and so on); they must never reach RDS.
unset DB_HOST DB_PORT DB_NAME DB_USERNAME DB_PASSWORD
for p in "${rds_prefixes[@]}"; do
	unset "${p}_DB_NAME" "${p}_DB_USERNAME" "${p}_DB_PASSWORD"
done
settings_source=".env (RDS_* variables)"
restore_shell_env
if [[ -n "${RDS_DB_HOST-}" ]]; then
	# Copy RDS_<NAME> to <NAME>, unless the shell exported <NAME> itself.
	for name in DB_HOST DB_PORT $(for p in "${rds_prefixes[@]}"; do echo "${p}_DB_NAME ${p}_DB_USERNAME ${p}_DB_PASSWORD"; done); do
		rds_name="RDS_$name"
		if ! shell_has "$name" && [[ -n "${!rds_name-}" ]]; then
			export "$name=${!rds_name}"
		fi
	done
elif [[ -f "$root/.env.rds" ]]; then
	settings_source=".env.rds (legacy; move these values into .env as RDS_* variables)"
	echo "Note: reading $root/.env.rds. Move its values into .env with an RDS_ prefix (see .env.example)." >&2
	load_env_file "$root/.env.rds"
	restore_shell_env
else
	fail "no AWS RDS settings found. Add the RDS_* variables to $root/.env (see the AWS RDS section of .env.example)."
fi
# Only the generic DB_* variables below reach the Java process.
for name in $(compgen -e | grep '^RDS_' || true); do unset "$name"; done
validate_jwt_secret

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
	fail "missing or empty for $service: ${missing[*]} (from $settings_source)
Set RDS_DB_HOST and RDS_${prefix}_DB_NAME / _DB_USERNAME / _DB_PASSWORD in $root/.env, or export them."
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

# Flyway opens the first database connection at startup. On flaky networks (for
# example a phone hotspot) looking up the RDS host name sometimes fails for a moment,
# so retry instead of exiting: waits of 1, 2, 4, 8, then 10 seconds, about a minute
# and a half in all. Exported values from your shell win.
export SPRING_FLYWAY_CONNECT_RETRIES="${SPRING_FLYWAY_CONNECT_RETRIES:-10}"
export SPRING_FLYWAY_CONNECT_RETRIES_INTERVAL="${SPRING_FLYWAY_CONNECT_RETRIES_INTERVAL:-10}"

# Each service owns exactly one database; catch a copy-paste mix-up in the settings.
expected_database="${service%-service}_db"
[[ "$DB_NAME" == "$expected_database" ]] ||
	fail "$name_var is '$DB_NAME', but $service must use its own database '$expected_database'."

cat <<EOF
run-service.sh: $service against AWS RDS (Spring profile '$SPRING_PROFILES_ACTIVE')
  host:     $DB_HOST
  port:     $DB_PORT
  database: $DB_NAME
  user:     $DB_USERNAME
  password: set (not shown)
  settings: $settings_source
EOF
[[ "${DRY_RUN:-}" == 1 ]] && exit 0
apply_service_jvm_options
cd "$dir"
exec ./mvnw spring-boot:run
