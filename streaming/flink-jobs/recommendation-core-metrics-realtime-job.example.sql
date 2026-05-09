-- Kafka/Flink 增强版示例：
-- 基于 dwd.recommendation_event 生成推荐看板核心 8 指标实时数据
-- 指标: DAU / CTR / CVR / GMV / 客单价 / 复购率 / 7日留存 / 退款率

CREATE TABLE dwd_recommendation_event (
  user_id BIGINT,
  product_id BIGINT,
  event_type STRING,
  scene STRING,
  trace_id STRING,
  recommendation_token STRING,
  experiment_group STRING,
  duration INT,
  order_id BIGINT,
  amount DOUBLE,
  event_time TIMESTAMP(3),
  event_day STRING,
  WATERMARK FOR event_time AS event_time - INTERVAL '5' SECOND
) WITH (
  'connector' = 'kafka',
  'topic' = 'dwd.recommendation_event',
  'properties.bootstrap.servers' = 'localhost:9092',
  'properties.group.id' = 'flink-rec-kpi-realtime',
  'scan.startup.mode' = 'latest-offset',
  'format' = 'json'
);

CREATE TABLE dws_recommendation_core_metrics_realtime (
  statDate STRING,
  dau BIGINT,
  exposureCount BIGINT,
  clickCount BIGINT,
  orderCount BIGINT,
  refundCount BIGINT,
  gmv DOUBLE,
  refundAmount DOUBLE,
  ctr DOUBLE,
  cvr DOUBLE,
  avgOrderValue DOUBLE,
  repurchaseRate DOUBLE,
  retention7d DOUBLE,
  refundRate DOUBLE,
  orderUserCount BIGINT,
  repurchaseUserCount BIGINT,
  retainedUserCount BIGINT,
  retentionBaseUserCount BIGINT,
  ts STRING
) WITH (
  'connector' = 'kafka',
  'topic' = 'dws.recommendation_core_metrics_realtime',
  'properties.bootstrap.servers' = 'localhost:9092',
  'format' = 'json'
);

CREATE VIEW rec_daily_base AS
SELECT
  DATE_FORMAT(event_time, 'yyyy-MM-dd') AS stat_date,
  COUNT(DISTINCT user_id) AS dau,
  SUM(CASE WHEN event_type = 'exposure' THEN 1 ELSE 0 END) AS exposure_count,
  SUM(CASE WHEN event_type = 'click' THEN 1 ELSE 0 END) AS click_count,
  SUM(CASE WHEN event_type = 'order' THEN 1 ELSE 0 END) AS order_count,
  SUM(CASE WHEN event_type = 'refund' THEN 1 ELSE 0 END) AS refund_count,
  SUM(CASE WHEN event_type = 'order' THEN COALESCE(amount, 0.0) ELSE 0.0 END) AS gmv,
  SUM(CASE WHEN event_type = 'refund' THEN COALESCE(amount, 0.0) ELSE 0.0 END) AS refund_amount
FROM dwd_recommendation_event
GROUP BY DATE_FORMAT(event_time, 'yyyy-MM-dd');

CREATE VIEW rec_daily_order_user AS
SELECT
  DATE_FORMAT(event_time, 'yyyy-MM-dd') AS stat_date,
  user_id,
  COUNT(*) AS order_cnt
FROM dwd_recommendation_event
WHERE event_type = 'order'
GROUP BY DATE_FORMAT(event_time, 'yyyy-MM-dd'), user_id;

CREATE VIEW rec_daily_repurchase AS
SELECT
  stat_date,
  COUNT(*) AS order_user_count,
  SUM(CASE WHEN order_cnt >= 2 THEN 1 ELSE 0 END) AS repurchase_user_count
FROM rec_daily_order_user
GROUP BY stat_date;

CREATE VIEW rec_daily_active_user AS
SELECT
  DATE_FORMAT(event_time, 'yyyy-MM-dd') AS stat_date,
  user_id
FROM dwd_recommendation_event
GROUP BY DATE_FORMAT(event_time, 'yyyy-MM-dd'), user_id;

CREATE VIEW rec_retention_base_7d AS
SELECT
  DATE_FORMAT(DATE_ADD(TO_DATE(stat_date), 7), 'yyyy-MM-dd') AS stat_date,
  COUNT(*) AS base_user_count_7d
FROM rec_daily_active_user
GROUP BY DATE_FORMAT(DATE_ADD(TO_DATE(stat_date), 7), 'yyyy-MM-dd');

CREATE VIEW rec_retention_hit_7d AS
SELECT
  cur.stat_date AS stat_date,
  COUNT(*) AS retained_user_count_7d
FROM rec_daily_active_user cur
JOIN rec_daily_active_user base
  ON cur.user_id = base.user_id
 AND cur.stat_date = DATE_FORMAT(DATE_ADD(TO_DATE(base.stat_date), 7), 'yyyy-MM-dd')
GROUP BY cur.stat_date;

INSERT INTO dws_recommendation_core_metrics_realtime
SELECT
  base.stat_date AS statDate,
  base.dau AS dau,
  base.exposure_count AS exposureCount,
  base.click_count AS clickCount,
  base.order_count AS orderCount,
  base.refund_count AS refundCount,
  CAST(base.gmv AS DOUBLE) AS gmv,
  CAST(base.refund_amount AS DOUBLE) AS refundAmount,
  CAST(ROUND(CASE WHEN base.exposure_count > 0 THEN (base.click_count * 100.0) / base.exposure_count ELSE 0.0 END, 4) AS DOUBLE) AS ctr,
  CAST(ROUND(CASE WHEN base.click_count > 0 THEN (base.order_count * 100.0) / base.click_count ELSE 0.0 END, 4) AS DOUBLE) AS cvr,
  CAST(ROUND(CASE WHEN base.order_count > 0 THEN base.gmv / base.order_count ELSE 0.0 END, 4) AS DOUBLE) AS avgOrderValue,
  CAST(ROUND(
    CASE WHEN COALESCE(repurchase.order_user_count, 0) > 0
      THEN (COALESCE(repurchase.repurchase_user_count, 0) * 100.0) / repurchase.order_user_count
      ELSE 0.0
    END, 4) AS DOUBLE) AS repurchaseRate,
  CAST(ROUND(
    CASE WHEN COALESCE(retention_base.base_user_count_7d, 0) > 0
      THEN (COALESCE(retention_hit.retained_user_count_7d, 0) * 100.0) / retention_base.base_user_count_7d
      ELSE 0.0
    END, 4) AS DOUBLE) AS retention7d,
  CAST(ROUND(CASE WHEN base.order_count > 0 THEN (base.refund_count * 100.0) / base.order_count ELSE 0.0 END, 4) AS DOUBLE) AS refundRate,
  COALESCE(repurchase.order_user_count, 0) AS orderUserCount,
  COALESCE(repurchase.repurchase_user_count, 0) AS repurchaseUserCount,
  COALESCE(retention_hit.retained_user_count_7d, 0) AS retainedUserCount,
  COALESCE(retention_base.base_user_count_7d, 0) AS retentionBaseUserCount,
  DATE_FORMAT(CURRENT_TIMESTAMP, 'yyyy-MM-dd''T''HH:mm:ss') AS ts
FROM rec_daily_base base
LEFT JOIN rec_daily_repurchase repurchase
  ON base.stat_date = repurchase.stat_date
LEFT JOIN rec_retention_base_7d retention_base
  ON base.stat_date = retention_base.stat_date
LEFT JOIN rec_retention_hit_7d retention_hit
  ON base.stat_date = retention_hit.stat_date;
