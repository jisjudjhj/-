-- DWD 标准化示例：
-- 将 Debezium 的 cdc.orders + cdc.order_item 标准化为 dwd.order_paid_event

CREATE TABLE cdc_orders (
  id BIGINT,
  user_id BIGINT,
  status INT,
  total_amount DECIMAL(16, 2),
  pay_time TIMESTAMP(3),
  PRIMARY KEY (id) NOT ENFORCED
) WITH (
  'connector' = 'kafka',
  'topic' = 'cdc.orders',
  'properties.bootstrap.servers' = 'localhost:9092',
  'properties.group.id' = 'flink-dwd-orders',
  'scan.startup.mode' = 'latest-offset',
  'format' = 'debezium-json',
  'debezium-json.schema-include' = 'false'
);

CREATE TABLE cdc_order_item (
  id BIGINT,
  order_id BIGINT,
  product_id BIGINT,
  quantity INT,
  subtotal DECIMAL(16, 2),
  PRIMARY KEY (id) NOT ENFORCED
) WITH (
  'connector' = 'kafka',
  'topic' = 'cdc.order_item',
  'properties.bootstrap.servers' = 'localhost:9092',
  'properties.group.id' = 'flink-dwd-order-item',
  'scan.startup.mode' = 'latest-offset',
  'format' = 'debezium-json',
  'debezium-json.schema-include' = 'false'
);

CREATE TABLE dwd_order_paid_event (
  order_id BIGINT,
  user_id BIGINT,
  product_id BIGINT,
  quantity INT,
  paid_amount DOUBLE,
  event_time TIMESTAMP(3),
  source STRING,
  ts STRING
) WITH (
  'connector' = 'kafka',
  'topic' = 'dwd.order_paid_event',
  'properties.bootstrap.servers' = 'localhost:9092',
  'format' = 'json'
);

INSERT INTO dwd_order_paid_event
SELECT
  o.id AS order_id,
  o.user_id,
  i.product_id,
  i.quantity,
  CAST(i.subtotal AS DOUBLE) AS paid_amount,
  COALESCE(o.pay_time, CURRENT_TIMESTAMP) AS event_time,
  'order_paid' AS source,
  DATE_FORMAT(COALESCE(o.pay_time, CURRENT_TIMESTAMP), 'yyyy-MM-dd''T''HH:mm:ss') AS ts
FROM cdc_orders o
JOIN cdc_order_item i ON o.id = i.order_id
WHERE o.status = 1
  AND o.pay_time IS NOT NULL
  AND i.product_id IS NOT NULL;
