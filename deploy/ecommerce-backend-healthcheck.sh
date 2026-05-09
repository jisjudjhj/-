#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="${SERVICE_NAME:-ecommerce-backend.service}"
CHECK_URL="${CHECK_URL:-http://127.0.0.1:8080/ws/info}"
STATE_DIR="${STATE_DIR:-/var/lib/ecommerce-backend-health}"
STATE_FILE="${STATE_DIR}/state.env"
FAILURE_THRESHOLD="${FAILURE_THRESHOLD:-3}"
RESTART_COOLDOWN_SEC="${RESTART_COOLDOWN_SEC:-1800}"
RESTART_WINDOW_SEC="${RESTART_WINDOW_SEC:-21600}"
MAX_RESTARTS_WINDOW="${MAX_RESTARTS_WINDOW:-2}"
CURL_TIMEOUT_SEC="${CURL_TIMEOUT_SEC:-8}"

mkdir -p "${STATE_DIR}"

failure_count=0
last_failure_ts=0
last_restart_ts=0
restart_window_start_ts=0
restart_window_count=0

if [[ -f "${STATE_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${STATE_FILE}"
fi

now="$(date +%s)"
http_code="000"
service_state="$(systemctl is-active "${SERVICE_NAME}" 2>/dev/null || true)"

if [[ "${service_state}" == "activating" ]]; then
  logger -t ecommerce-backend-healthcheck "startup_grace service=${service_state}"
  exit 0
fi

if [[ "${service_state}" == "active" ]]; then
  http_code="$(curl -sS -o /dev/null -m "${CURL_TIMEOUT_SEC}" -w "%{http_code}" "${CHECK_URL}?t=${now}" || true)"
fi

save_state() {
  cat >"${STATE_FILE}" <<EOF
failure_count=${failure_count}
last_failure_ts=${last_failure_ts}
last_restart_ts=${last_restart_ts}
restart_window_start_ts=${restart_window_start_ts}
restart_window_count=${restart_window_count}
EOF
}

if [[ "${service_state}" == "active" && "${http_code}" == "200" ]]; then
  failure_count=0
  last_failure_ts=0
  save_state
  logger -t ecommerce-backend-healthcheck "healthy service=${service_state} http_code=${http_code}"
  exit 0
fi

failure_count=$((failure_count + 1))
last_failure_ts="${now}"
reason="service=${service_state} http_code=${http_code}"

if (( failure_count < FAILURE_THRESHOLD )); then
  save_state
  logger -t ecommerce-backend-healthcheck "probe_failed count=${failure_count}/${FAILURE_THRESHOLD} ${reason}"
  exit 0
fi

if (( restart_window_start_ts == 0 || now - restart_window_start_ts > RESTART_WINDOW_SEC )); then
  restart_window_start_ts="${now}"
  restart_window_count=0
fi

if (( last_restart_ts > 0 && now - last_restart_ts < RESTART_COOLDOWN_SEC )); then
  save_state
  logger -t ecommerce-backend-healthcheck "restart_skipped cooldown remaining=$((RESTART_COOLDOWN_SEC - (now - last_restart_ts))) ${reason}"
  exit 0
fi

if (( restart_window_count >= MAX_RESTARTS_WINDOW )); then
  save_state
  logger -t ecommerce-backend-healthcheck "restart_skipped window_limit=${restart_window_count}/${MAX_RESTARTS_WINDOW} ${reason}"
  exit 0
fi

logger -t ecommerce-backend-healthcheck "restart_triggered count=${failure_count} ${reason}"
systemctl reset-failed "${SERVICE_NAME}" >/dev/null 2>&1 || true

if systemctl restart "${SERVICE_NAME}"; then
  last_restart_ts="${now}"
  restart_window_count=$((restart_window_count + 1))
  failure_count=0
  save_state
  logger -t ecommerce-backend-healthcheck "restart_success window_restarts=${restart_window_count}/${MAX_RESTARTS_WINDOW}"
  exit 0
fi

save_state
logger -t ecommerce-backend-healthcheck "restart_failed ${reason}"
exit 0
