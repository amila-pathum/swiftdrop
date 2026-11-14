#!/usr/bin/env bash
set -euo pipefail
set -m
windows_shell=1
profile=rds
stopped=0
launch() {
    if ((windows_shell)); then
        exec "$@"
    else
        exec setsid "$@"
    fi
}
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

launch bash -c 'sleep 60 & wait' &
pid=$!
pids=("$pid")
sleep 1
kill -0 "$pid"
stop_all
if kill -0 "$pid" 2>/dev/null; then
    echo "FAIL: launched job survived cleanup" >&2
    exit 1
fi
echo "PASS: Git Bash launch and cleanup"
