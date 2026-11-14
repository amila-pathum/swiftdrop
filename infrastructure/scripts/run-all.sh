#!/usr/bin/env bash
# Starts the whole project in one terminal: the seven backend services, the
# API Gateway and the frontend. Press Ctrl+C to stop everything.
#
# Local Docker PostgreSQL (default; also starts docker compose):
#   infrastructure/scripts/run-all.sh
# AWS RDS (database settings from .env.rds; Docker is not started):
#   DB_PROFILE=rds infrastructure/scripts/run-all.sh
# Only some parts (names as in backend/, plus "frontend"):
#   infrastructure/scripts/run-all.sh identity-service parcel-service api-gateway frontend
#
# Each part's output goes to logs/<name>.log (ignored by Git). Options:
#   SKIP_DOCKER=1   do not run "docker compose up" in local mode
#   START_DELAY=3   seconds between starting two services (default 3)
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
run_service="$root/infrastructure/scripts/run-service.sh"
profile="${DB_PROFILE:-local}"
logs="$root/logs"
start_delay="${START_DELAY:-3}"

database_services=(identity-service parcel-service merchant-service courier-service
	dispatch-service pricing-service notification-service)
all_parts=("${database_services[@]}" api-gateway frontend)
parts=("$@")
((${#parts[@]})) || parts=("${all_parts[@]}")

case "$profile" in
local | rds) ;;
*) echo "DB_PROFILE must be 'local' (the default) or 'rds', not '$profile'." >&2; exit 1 ;;
esac
for part in "${parts[@]}"; do
	[[ " ${all_parts[*]} " == *" $part "* ]] || {
		echo "Unknown part '$part'. Choose from: ${all_parts[*]}" >&2
		exit 1
	}
done
# Git Bash has no setsid. Bash job control gives each background job its own
# process group; taskkill additionally stops native Windows descendants.
windows_shell=0
case "$(uname -s)" in MINGW*|MSYS*) windows_shell=1 ;; esac
if ((windows_shell)); then
    command -v taskkill.exe >/dev/null || { echo "taskkill.exe is required." >&2; exit 1; }
    set -m
elif ! command -v setsid >/dev/null; then
    echo "setsid is required on this platform; use Linux/WSL or Git Bash." >&2
    exit 1
fi
launch() {
    if ((windows_shell)); then
        exec "$@"
    else
        exec setsid "$@"
    fi
}
command -v curl >/dev/null || { echo "curl is required." >&2; exit 1; }
if [[ ",${SPRING_PROFILES_ACTIVE-}," == *,rds,* ]]; then
	echo "Unset SPRING_PROFILES_ACTIVE and use DB_PROFILE=rds instead." >&2
	exit 1
fi

# Ports, from .env when set there (read only *_PORT lines; nothing is executed).
env_port() {
	local value=""
	[[ -f "$root/.env" ]] && value="$(grep -E "^$1=[0-9]+\s*$" "$root/.env" | tail -n1 | cut -d= -f2 | tr -d '[:space:]')" || true
	echo "${!1:-${value:-$2}}"
}
port_of() {
	case "$1" in
	identity-service) env_port IDENTITY_SERVICE_PORT 8081 ;;
	parcel-service) env_port PARCEL_SERVICE_PORT 8082 ;;
	merchant-service) env_port MERCHANT_SERVICE_PORT 8083 ;;
	courier-service) env_port COURIER_SERVICE_PORT 8084 ;;
	dispatch-service) env_port DISPATCH_SERVICE_PORT 8085 ;;
	pricing-service) env_port PRICING_SERVICE_PORT 8086 ;;
	notification-service) env_port NOTIFICATION_SERVICE_PORT 8087 ;;
	api-gateway) env_port GATEWAY_PORT 8080 ;;
	frontend) echo 3000 ;;
	esac
}
health_url() {
	if [[ "$1" == frontend ]]; then
		echo "http://localhost:3000/"
	else
		echo "http://localhost:$(port_of "$1")/actuator/health"
	fi
}
port_in_use() {
	if ((windows_shell)); then
        netstat.exe -ano -p tcp | awk -v port=":$1" '$2 ~ (port "$") && $4 == "LISTENING" { found=1 }
            END { exit !found }'
    else
        ss -ltnH "sport = :$1" 2>/dev/null | grep -q .
    fi
}

# ---- Checks before anything starts ----
for part in "${parts[@]}"; do
	port="$(port_of "$part")"
	if port_in_use "$port"; then
		echo "Port $port ($part) is already in use. Stop whatever runs there first, for example:" >&2
		echo "  ss -ltnp 'sport = :$port'" >&2
		exit 1
	fi
done
if [[ "$profile" == rds ]]; then
	echo "Checking .env.rds for every database service ..."
	for part in "${parts[@]}"; do
		[[ " ${database_services[*]} " == *" $part "* ]] || continue
		# Validates this service's variables without starting it; prints no password.
		DRY_RUN=1 DB_PROFILE=rds "$run_service" "$part" >/dev/null ||
			{ echo "Nothing was started." >&2; exit 1; }
	done
	echo "  all set."
	if [[ "${SKIP_DOCKER:-}" != 1 ]]; then
		echo "Starting RabbitMQ (docker compose) ..."
		(cd "$root" && docker compose up -d --wait rabbitmq) || {
			echo "docker compose failed for RabbitMQ; is Docker running? (SKIP_DOCKER=1 skips this step)" >&2
			exit 1
		}
	fi
elif [[ "${SKIP_DOCKER:-}" != 1 ]]; then
	echo "Starting PostgreSQL and RabbitMQ (docker compose) ..."
	(cd "$root" && docker compose up -d --wait) || {
		echo "docker compose failed; is Docker running? (SKIP_DOCKER=1 skips this step)" >&2
		exit 1
	}
fi

# ---- Start ----
mkdir -p "$logs"
pids=()
names=()
stopped=0
stop_all() {
	((stopped)) && return
	stopped=1
	trap - INT TERM EXIT
	echo
	echo "Stopping everything ..."
	local pid winpid
    if ((windows_shell)); then
        for pid in "${pids[@]}"; do
            # MSYS PID and Windows PID may differ. Resolve only jobs we launched.
            winpid="$(ps -p "$pid" | awk 'NR==1 { for(i=1;i<=NF;i++) if($i=="WINPID") col=i }
                NR>1 && col { print $col; exit }')"
            if [[ "$winpid" =~ ^[0-9]+$ ]]; then
                MSYS_NO_PATHCONV=1 taskkill.exe /PID "$winpid" /T /F >/dev/null 2>&1 || true
            fi
        done
    fi
	for pid in "${pids[@]}"; do
		kill -TERM -- "-$pid" 2>/dev/null || true
	done
	for _ in {1..20}; do
		local alive=0
		for pid in "${pids[@]}"; do kill -0 -- "-$pid" 2>/dev/null && alive=1; done
		((alive)) || break
		sleep 0.5
	done
	for pid in "${pids[@]}"; do kill -KILL -- "-$pid" 2>/dev/null || true; done
	echo "Stopped.$([[ "$profile" == local && "${SKIP_DOCKER:-}" != 1 ]] && echo " Docker containers keep running; stop them with: docker compose stop")"
}
# Ctrl+C: stop everything and end the script (the handler must not return
# to the loops below). EXIT: also stop everything after a failure.
trap 'stop_all; exit 0' INT TERM
trap stop_all EXIT

start_part() {
	local part=$1 log="$logs/$1.log"
	: >"$log"
	if [[ "$part" == frontend ]]; then
		if [[ ! -d "$root/frontend/node_modules" ]]; then
			echo "  installing frontend dependencies (npm install) ..."
			(cd "$root/frontend" && npm install) >>"$log" 2>&1
		fi
		(cd "$root/frontend" && launch npm run dev) >>"$log" 2>&1 &
	elif [[ "$part" == api-gateway ]]; then
		# The gateway has no database, so it always runs without DB_PROFILE.
		launch env -u DB_PROFILE bash "$run_service" "$part" >>"$log" 2>&1 &
	else
		DB_PROFILE="$profile" launch bash "$run_service" "$part" >>"$log" 2>&1 &
	fi
	pids+=("$!")
	names+=("$part")
	printf '  started %-22s port %-5s log %s\n' "$part" "$(port_of "$part")" "${log#"$root"/}"
}

echo "Starting ${#parts[@]} part(s) with DB_PROFILE=$profile ..."
first=1
for part in "${parts[@]}"; do
	((first)) || sleep "$start_delay"
	first=0
	start_part "$part"
done

# ---- Wait until each part answers ----
echo "Waiting for everything to come up (the first start can take a few minutes) ..."
declare -A ready=()
deadline=$((SECONDS + 300))
while ((${#ready[@]} < ${#names[@]})); do
	for i in "${!names[@]}"; do
		part="${names[$i]}"
		[[ -n "${ready[$part]-}" ]] && continue
		if ! kill -0 "${pids[$i]}" 2>/dev/null; then
			echo "FAILED: $part exited. Last lines of logs/$part.log:" >&2
			tail -n 25 "$logs/$part.log" >&2
			exit 1
		fi
		if curl -fsS -o /dev/null --max-time 2 "$(health_url "$part")" 2>/dev/null; then
			ready[$part]=1
			printf '  ready   %-22s %s\n' "$part" "$(health_url "$part")"
		fi
	done
	if ((SECONDS > deadline)); then
		echo "Timed out after 5 minutes. Not ready:" >&2
		for part in "${names[@]}"; do [[ -n "${ready[$part]-}" ]] || echo "  $part (see logs/$part.log)" >&2; done
		echo "They keep starting in the background; press Ctrl+C to stop everything." >&2
		break
	fi
	sleep 2
done

echo
echo "SwiftDrop is running (database: $profile)."
[[ " ${parts[*]} " == *" frontend "* ]] && echo "  Open http://localhost:3000"
echo "  Logs: tail -f logs/<name>.log      Stop everything: Ctrl+C"

# Keep running until Ctrl+C, or until a part stops on its own.
while true; do
	for i in "${!names[@]}"; do
		if ! kill -0 "${pids[$i]}" 2>/dev/null; then
			echo "${names[$i]} stopped unexpectedly. Last lines of logs/${names[$i]}.log:" >&2
			tail -n 25 "$logs/${names[$i]}.log" >&2
			exit 1
		fi
	done
	sleep 3
done
