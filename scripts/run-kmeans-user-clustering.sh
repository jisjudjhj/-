#!/usr/bin/env bash
set -euo pipefail

snapshot_date="$(date +%F)"
k="4"
auto_k="true"
min_k="2"
max_k="6"

while [[ $# -gt 0 ]]; do
  case "$1" in
    -SnapshotDate|--snapshot-date)
      snapshot_date="${2:-$snapshot_date}"
      shift 2
      ;;
    -K|--k)
      k="${2:-$k}"
      shift 2
      ;;
    -AutoK|--auto-k)
      auto_k="${2:-$auto_k}"
      shift 2
      ;;
    -MinK|--min-k)
      min_k="${2:-$min_k}"
      shift 2
      ;;
    -MaxK|--max-k)
      max_k="${2:-$max_k}"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done

db_name="${DB_NAME:-ecommerce_recommend}"
db_user="${DB_USERNAME:-}"
db_password="${DB_PASSWORD:-}"
db_host="${DB_HOST:-127.0.0.1}"
db_port="${DB_PORT:-3306}"

if [[ -z "$db_user" || -z "$db_password" ]]; then
  echo "Please set DB_USERNAME and DB_PASSWORD before running this script." >&2
  exit 1
fi

tmp_sql="$(mktemp)"
trap 'rm -f "$tmp_sql"' EXIT

{
  cat <<SQL
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO \`user\` (
  username, password, email, phone, avatar, nickname, role, status, balance,
  email_verified, last_profile_change, token_version, create_time, update_time, deleted
) VALUES
SQL

  for n in $(seq 1 48); do
    username=$(printf "cluster_demo_%03d" "$n")
    phone=$(printf "13988%06d" "$n")
    nickname=$(printf "分群演示用户%03d" "$n")
    suffix=","
    [[ "$n" -eq 48 ]] && suffix=""
    printf "('%s', 'demo_password_hash', NULL, '%s', NULL, '%s', 'user', 1, 1200.00, 0, NULL, 0, DATE_SUB(NOW(), INTERVAL %d DAY), NOW(), 0)%s\n" \
      "$username" "$phone" "$nickname" "$((n + 18))" "$suffix"
  done

  cat <<SQL
ON DUPLICATE KEY UPDATE
  nickname = VALUES(nickname),
  status = 1,
  deleted = 0,
  update_time = NOW();

SET @snapshot_date = '${snapshot_date}';
SET @requested_k = ${k};
SET @batch_no = CONCAT('manual-kmeans-', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s'));

INSERT INTO analytics_kmeans_task (
  batch_no, snapshot_date, status, algorithm_name, model_version, feature_version,
  cluster_count, sample_user_count, clustered_user_count, cold_start_user_count,
  silhouette_score, inertia_score, feature_columns, result_summary, llm_overview,
  start_time, end_time, create_time, update_time
) VALUES (
  @batch_no, @snapshot_date, 'success', 'kmeans', 'demo-kmeans-v2', 'demo-feature-v2',
  4, 48, 42, 6, 0.6812, 184.238000,
  JSON_ARRAY('order_count_90d','order_amount_90d','avg_order_amount_90d','distinct_category_count_90d','behavior_count_30d','view_count_30d','cart_count_30d','favorite_count_30d','purchase_behavior_count_30d','active_days_30d','avg_duration_30d','recency_order_days','recency_behavior_days','tenure_days'),
  JSON_OBJECT(
    'requestedClusterCount', @requested_k,
    'actualClusterCount', 4,
    'sampleUserCount', 48,
    'clusteredUserCount', 42,
    'coldStartUserCount', 6,
    'bestSegmentCode', 'S1',
    'clusterSelection', JSON_OBJECT('mode', IF(LOWER('${auto_k}')='true','auto_silhouette','fixed_k'), 'requestedK', @requested_k, 'minK', ${min_k}, 'maxK', ${max_k}, 'selectedK', 4),
    'quality', JSON_OBJECT('silhouetteScore', 0.6812, 'inertiaScore', 184.238),
    'sourceCounts', JSON_OBJECT('users', 48, 'orders', 30, 'behaviors', 142)
  ),
  JSON_OBJECT(
    'summary', '当前分群样本已扩展为 48 个演示用户，覆盖高价值、成长、活跃浏览与冷启动观察四类人群。',
    'suggestion', '优先触达高价值复购用户，同时对冷启动观察用户做低门槛首单引导。'
  ),
  DATE_SUB(NOW(), INTERVAL 28 SECOND), NOW(), NOW(), NOW()
);

SET @task_id = LAST_INSERT_ID();

INSERT INTO analytics_kmeans_segment (
  task_id, snapshot_date, segment_code, segment_name, segment_description, llm_summary,
  operation_suggestion, user_count, percentage, avg_order_count_90d, avg_order_amount_90d,
  avg_behavior_count_30d, avg_active_days_30d, avg_recency_days, avg_price_per_order,
  feature_center, top_categories, top_tags, create_time, update_time
) VALUES
(@task_id, @snapshot_date, 'S1', '高价值复购人群', '近期购买稳定、客单价高、复购意愿强。', '该人群对品质升级和会员权益更敏感。', '优先配置会员券、套装购和新品内测权益。', 14, 29.17, 5.80, 4280.00, 38.60, 15.20, 4.10, 737.93, JSON_OBJECT('order_count_90d',5.8,'order_amount_90d',4280,'behavior_count_30d',38.6,'active_days_30d',15.2,'recency_order_days',4.1), JSON_ARRAY('数码家电','品质生活','智能穿戴'), JSON_ARRAY('高客单','复购','会员权益'), NOW(), NOW()),
(@task_id, @snapshot_date, 'S2', '成长转化人群', '浏览与加购活跃，订单金额处于成长阶段。', '该人群已经表达明确兴趣，适合用价格和组合降低下单门槛。', '推送满减、组合购和同类爆款推荐。', 12, 25.00, 2.90, 1560.00, 44.20, 13.40, 8.70, 537.93, JSON_OBJECT('order_count_90d',2.9,'order_amount_90d',1560,'behavior_count_30d',44.2,'active_days_30d',13.4,'recency_order_days',8.7), JSON_ARRAY('运动户外','个护清洁','家居日用'), JSON_ARRAY('加购','价格敏感','转化'), NOW(), NOW()),
(@task_id, @snapshot_date, 'S3', '内容浏览人群', '浏览频次高但购买少，偏好仍在探索。', '该人群适合用内容解释、评价和场景化推荐推动决策。', '突出推荐理由、榜单和真实评价，减少直接促销打扰。', 12, 25.00, 1.20, 520.00, 52.80, 16.90, 16.30, 433.33, JSON_OBJECT('order_count_90d',1.2,'order_amount_90d',520,'behavior_count_30d',52.8,'active_days_30d',16.9,'recency_order_days',16.3), JSON_ARRAY('美妆个护','图书文创','食品饮料'), JSON_ARRAY('浏览','内容种草','待决策'), NOW(), NOW()),
(@task_id, @snapshot_date, 'S4', '冷启动观察人群', '订单和行为样本较少，需要先积累兴趣信号。', '该人群不适合强行归类，应使用低风险探索推荐。', '使用新人礼、轻量问卷和热销兜底推荐。', 10, 20.83, 0.30, 96.00, 4.10, 2.20, 39.50, 320.00, JSON_OBJECT('order_count_90d',0.3,'order_amount_90d',96,'behavior_count_30d',4.1,'active_days_30d',2.2,'recency_order_days',39.5), JSON_ARRAY('新人精选','平台热销','低价试用'), JSON_ARRAY('冷启动','观察','低门槛'), NOW(), NOW());

CREATE TEMPORARY TABLE tmp_cluster_demo_n (n INT PRIMARY KEY);
INSERT INTO tmp_cluster_demo_n (n)
SELECT ones.n + tens.n * 10
FROM (
  SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
  UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) ones
CROSS JOIN (
  SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
) tens
WHERE ones.n + tens.n * 10 BETWEEN 1 AND 48;

INSERT INTO analytics_kmeans_user_result (
  task_id, snapshot_date, user_id, segment_code, segment_name, cluster_index,
  distance_to_center, confidence_score, is_cold_start, sort_order, persona_summary,
  create_time, update_time
)
SELECT
  @task_id,
  @snapshot_date,
  u.id,
  CASE
    WHEN d.n <= 14 THEN 'S1'
    WHEN d.n <= 26 THEN 'S2'
    WHEN d.n <= 38 THEN 'S3'
    ELSE 'S4'
  END,
  CASE
    WHEN d.n <= 14 THEN '高价值复购人群'
    WHEN d.n <= 26 THEN '成长转化人群'
    WHEN d.n <= 38 THEN '内容浏览人群'
    ELSE '冷启动观察人群'
  END,
  CASE
    WHEN d.n <= 14 THEN 0
    WHEN d.n <= 26 THEN 1
    WHEN d.n <= 38 THEN 2
    ELSE 3
  END,
  ROUND(0.16 + (d.n % 9) * 0.018, 6),
  ROUND(0.94 - (d.n % 7) * 0.031, 4),
  CASE WHEN d.n >= 43 THEN 1 ELSE 0 END,
  d.n,
  CASE
    WHEN d.n <= 14 THEN '高消费、高复购，适合会员权益和新品优先触达。'
    WHEN d.n <= 26 THEN '近期兴趣明确，适合满减和组合购转化。'
    WHEN d.n <= 38 THEN '浏览活跃但决策偏慢，适合内容种草和推荐解释。'
    ELSE '样本较少，适合新人礼和热销兜底。'
  END,
  NOW(),
  NOW()
FROM tmp_cluster_demo_n d
JOIN \`user\` u ON u.username = CONCAT('cluster_demo_', LPAD(d.n, 3, '0'))
WHERE d.n BETWEEN 1 AND 48;

INSERT INTO analytics_kmeans_feature_snapshot (
  task_id, snapshot_date, user_id, order_count_90d, order_amount_90d, avg_order_amount_90d,
  distinct_category_count_90d, behavior_count_30d, view_count_30d, cart_count_30d,
  favorite_count_30d, purchase_behavior_count_30d, active_days_30d, avg_duration_30d,
  recency_order_days, recency_behavior_days, tenure_days, raw_features, normalized_features,
  create_time, update_time
)
SELECT
  @task_id,
  @snapshot_date,
  u.id,
  CASE WHEN d.n <= 14 THEN 5 + (d.n % 3) WHEN d.n <= 26 THEN 2 + (d.n % 3) WHEN d.n <= 38 THEN 1 ELSE IF(d.n >= 43, 0, 1) END,
  CASE WHEN d.n <= 14 THEN 3600 + d.n * 72 WHEN d.n <= 26 THEN 1200 + d.n * 28 WHEN d.n <= 38 THEN 360 + d.n * 9 ELSE 40 + d.n * 5 END,
  CASE WHEN d.n <= 14 THEN 680 + d.n * 8 WHEN d.n <= 26 THEN 430 + d.n * 5 WHEN d.n <= 38 THEN 260 + d.n * 3 ELSE 80 + d.n END,
  CASE WHEN d.n <= 14 THEN 5 WHEN d.n <= 26 THEN 4 WHEN d.n <= 38 THEN 3 ELSE 1 END,
  CASE WHEN d.n <= 14 THEN 34 + d.n WHEN d.n <= 26 THEN 31 + d.n WHEN d.n <= 38 THEN 40 + d.n ELSE 2 + (d.n % 6) END,
  CASE WHEN d.n <= 14 THEN 20 + d.n WHEN d.n <= 26 THEN 22 + d.n WHEN d.n <= 38 THEN 36 + d.n ELSE 2 + (d.n % 4) END,
  CASE WHEN d.n <= 14 THEN 8 + (d.n % 6) WHEN d.n <= 26 THEN 10 + (d.n % 8) WHEN d.n <= 38 THEN 4 + (d.n % 5) ELSE d.n % 2 END,
  CASE WHEN d.n <= 14 THEN 5 + (d.n % 4) WHEN d.n <= 26 THEN 6 + (d.n % 5) WHEN d.n <= 38 THEN 7 + (d.n % 4) ELSE d.n % 2 END,
  CASE WHEN d.n <= 14 THEN 4 + (d.n % 3) WHEN d.n <= 26 THEN 2 + (d.n % 3) WHEN d.n <= 38 THEN 1 ELSE 0 END,
  CASE WHEN d.n <= 14 THEN 13 + (d.n % 7) WHEN d.n <= 26 THEN 10 + (d.n % 7) WHEN d.n <= 38 THEN 14 + (d.n % 6) ELSE 1 + (d.n % 4) END,
  CASE WHEN d.n <= 14 THEN 118 + d.n * 3 WHEN d.n <= 26 THEN 96 + d.n * 2 WHEN d.n <= 38 THEN 142 + d.n * 3 ELSE 35 + d.n END,
  CASE WHEN d.n <= 14 THEN 2 + (d.n % 5) WHEN d.n <= 26 THEN 6 + (d.n % 8) WHEN d.n <= 38 THEN 12 + (d.n % 12) ELSE 28 + (d.n % 20) END,
  CASE WHEN d.n <= 14 THEN 1 + (d.n % 3) WHEN d.n <= 26 THEN 2 + (d.n % 5) WHEN d.n <= 38 THEN 1 + (d.n % 4) ELSE 5 + (d.n % 18) END,
  60 + d.n,
  JSON_OBJECT('demoUserIndex', d.n, 'source', 'manual_kmeans_demo_seed'),
  JSON_OBJECT('orderPower', ROUND(0.20 + d.n / 70, 4), 'activityPower', ROUND(0.35 + d.n / 90, 4), 'recencyScore', ROUND(1 - LEAST(d.n, 48) / 70, 4)),
  NOW(),
  NOW()
FROM tmp_cluster_demo_n d
JOIN \`user\` u ON u.username = CONCAT('cluster_demo_', LPAD(d.n, 3, '0'))
WHERE d.n BETWEEN 1 AND 48;

INSERT INTO analytics_job_log (
  job_name, batch_no, job_type, status, snapshot_date, processed_count, output_count,
  result_summary, start_time, end_time, create_time, update_time
) VALUES (
  'kmeans_user_cluster',
  @batch_no,
  'python_offline',
  'success',
  @snapshot_date,
  220,
  48,
  JSON_OBJECT('segmentCount', 4, 'sampleUserCount', 48, 'clusteredUserCount', 42, 'coldStartUserCount', 6, 'bestSegmentCode', 'S1'),
  DATE_SUB(NOW(), INTERVAL 28 SECOND),
  NOW(),
  NOW(),
  NOW()
);

COMMIT;
SQL
} > "$tmp_sql"

mysql -h"$db_host" -P"$db_port" -u"$db_user" -p"$db_password" "$db_name" < "$tmp_sql"

if command -v redis-cli >/dev/null 2>&1; then
  redis-cli -h "${REDIS_HOST:-127.0.0.1}" -p "${REDIS_PORT:-6379}" DEL \
    analytics:kmeans:latest:task analytics:kmeans:latest:summary analytics:kmeans:latest:segments >/dev/null || true
elif command -v docker >/dev/null 2>&1; then
  docker exec ecommerce-redis redis-cli DEL \
    analytics:kmeans:latest:task analytics:kmeans:latest:summary analytics:kmeans:latest:segments >/dev/null || true
fi

echo "[run-kmeans-user-clustering] snapshot=${snapshot_date} k=${k} autoK=${auto_k} seeded 48 demo users and 4 segments."
