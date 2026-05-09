#!/usr/bin/env bash
set -euo pipefail

script_file=""
forward_args=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    -ExecutionPolicy)
      shift 2
      ;;
    -File)
      script_file="${2:-}"
      shift 2
      ;;
    *)
      forward_args+=("$1")
      shift
      ;;
  esac
done

case "$(basename "$script_file")" in
  run-kmeans-user-clustering.ps1)
    exec /opt/ecommerce/scripts/run-kmeans-user-clustering.sh "${forward_args[@]}"
    ;;
  run-python-analytics.ps1)
    jobs=""
    for ((i = 0; i < ${#forward_args[@]}; i++)); do
      if [[ "${forward_args[$i]}" == "-Jobs" || "${forward_args[$i]}" == "--jobs" ]]; then
        jobs="${forward_args[$((i + 1))]:-}"
        break
      fi
    done
    if [[ ",${jobs}," == *",kmeans,"* || -z "$jobs" ]]; then
      exec /opt/ecommerce/scripts/run-kmeans-user-clustering.sh "${forward_args[@]}"
    fi
    echo "[powershell-linux-compat] Skip non-kmeans analytics jobs: ${jobs:-<empty>}"
    exit 0
    ;;
  run-competition-bigdata.ps1)
    snapshot_date="$(date +%F)"
    while [[ $# -gt 0 ]]; do
      case "$1" in
        -SnapshotDate|--snapshot-date)
          snapshot_date="${2:-$snapshot_date}"
          shift 2
          ;;
        *)
          shift
          ;;
      esac
    done
    mkdir -p "/opt/ecommerce/backend/output/spark_competition/${snapshot_date}"
    echo "{\"available\":false,\"snapshotDate\":\"${snapshot_date}\",\"message\":\"Linux 兼容模式已接收竞赛产物刷新请求，当前环境未包含 Spark 产物生成脚本。\"}" \
      > "/opt/ecommerce/backend/output/spark_competition/${snapshot_date}/ads_competition_summary.json"
    echo "[powershell-linux-compat] Competition pipeline placeholder completed for ${snapshot_date}"
    exit 0
    ;;
  *)
    echo "Unsupported PowerShell compatibility target: ${script_file:-<empty>}" >&2
    exit 127
    ;;
esac
