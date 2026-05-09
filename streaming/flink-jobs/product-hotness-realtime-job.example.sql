-- Kafka/Flink 增强版示例：
-- 将用户行为 + 订单支付事件分别聚合为 1m / 1h / 1d 商品热度，输出到 dws.product_hotness_realtime

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
  'properties.group.id' = 'flink-product-hotness',
  'scan.startup.mode' = 'latest-offset',
  'format' = 'json'
);

CREATE TABLE dwd_order_paid_event (
  order_id BIGINT,
  user_id BIGINT,
  product_id BIGINT,
  quantity INT,
  paid_amount DOUBLE,
  event_time TIMESTAMP(3),
  WATERMARK FOR event_time AS event_time - INTERVAL '5' SECOND
) WITH (
  'connector' = 'kafka',
  'topic' = 'dwd.order_paid_event',
  'properties.bootstrap.servers' = 'localhost:9092',
  'properties.group.id' = 'flink-product-hotness-order-paid',
  'scan.startup.mode' = 'latest-offset',
  'format' = 'json'
);

CREATE TABLE dws_product_hotness_realtime (
  productId BIGINT,
  score DOUBLE,
  window STRING,
  ts STRING
) WITH (
  'connector' = 'kafka',
  'topic' = 'dws.product_hotness_realtime',
  'properties.bootstrap.servers' = 'localhost:9092',
  'format' = 'json'
);

CREATE VIEW stream_hot_events AS
SELECT
  product_id,
  event_time,
  CASE behavior_type
    WHEN 'purchase' THEN 8.0
    WHEN 'favorite' THEN 4.0
    WHEN 'cart' THEN 3.0
    WHEN 'view' THEN 1.0
    ELSE 0.5
  END AS hot_score
FROM dwd_user_behavior_event
UNION ALL
SELECT
  product_id,
  event_time,
  (COALESCE(quantity, 1) * 12.0) + (COALESCE(paid_amount, 0.0) * 0.04) AS hot_score
FROM dwd_order_paid_event;

INSERT INTO dws_product_hotness_realtime
SELECT
  product_id AS productId,
  CAST(SUM(hot_score) AS DOUBLE) AS score,
  '1m' AS window,
  DATE_FORMAT(window_end, 'yyyy-MM-dd''T''HH:mm:ss') AS ts
FROM TABLE(
  TUMBLE(TABLE stream_hot_events, DESCRIPTOR(event_time), INTERVAL '1' MINUTE)
)
GROUP BY product_id, window_start, window_end

UNION ALL

SELECT
  product_id AS productId,
  CAST(SUM(hot_score) AS DOUBLE) AS score,
  '1h' AS window,
  DATE_FORMAT(window_end, 'yyyy-MM-dd''T''HH:mm:ss') AS ts
FROM TABLE(
  TUMBLE(TABLE stream_hot_events, DESCRIPTOR(event_time), INTERVAL '1' HOUR)
)
GROUP BY product_id, window_start, window_end

UNION ALL

SELECT
  product_id AS productId,
  CAST(SUM(hot_score) AS DOUBLE) AS score,
  '1d' AS window,
  DATE_FORMAT(window_end, 'yyyy-MM-dd''T''HH:mm:ss') AS ts
FROM TABLE(
  TUMBLE(TABLE stream_hot_events, DESCRIPTOR(event_time), INTERVAL '1' DAY)
)
GROUP BY product_id, window_start, window_end;
