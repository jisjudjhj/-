-- Kafka/Flink 增强版示例：
-- 基于 DWD 标准行为事件聚合用户行为分布，再由后端 Kafka Consumer 写入 Redis 实时特征。

CREATE TABLE dwd_user_behavior_event (
  user_id BIGINT,
  product_id BIGINT,
  behavior_type STRING,
  event_time TIMESTAMP(3),
  WATERMARK FOR event_time AS event_time - INTERVAL '5' SECOND
) WITH (
  'connector' = 'kafka',
  'topic' = 'dwd.user_behavior_event',
  'properties.bootstrap.servers' = 'localhost:9092',
  'properties.group.id' = 'flink-user-behavior-dist',
  'scan.startup.mode' = 'latest-offset',
  'format' = 'json'
);

CREATE TABLE dws_user_behavior_distribution (
  userId BIGINT,
  behaviorType STRING,
  count BIGINT,
  productCount BIGINT,
  window STRING,
  ts STRING
) WITH (
  'connector' = 'kafka',
  'topic' = 'dws.user_behavior_distribution',
  'properties.bootstrap.servers' = 'localhost:9092',
  'format' = 'json'
);

INSERT INTO dws_user_behavior_distribution
SELECT
  user_id AS userId,
  behavior_type AS behaviorType,
  COUNT(*) AS count,
  COUNT(DISTINCT product_id) AS productCount,
  'all' AS window,
  DATE_FORMAT(CURRENT_TIMESTAMP, 'yyyy-MM-dd''T''HH:mm:ss') AS ts
FROM dwd_user_behavior_event
GROUP BY user_id, behavior_type;
