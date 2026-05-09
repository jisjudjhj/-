# Flink Jobs（MySQL CDC 直连版 + Kafka 增强版）

当前目录包含两类作业：

- **直连版**
  - Source：MySQL CDC
  - Runtime：本机 Flink
  - Sink：MySQL JDBC
- **增强版**
  - Source：Kafka CDC / DWD Topic
  - Runtime：Flink
  - Sink：DWS Kafka Topic

## 当前作业

### `user-behavior-distribution-job.sql.template`

作用：

实时监听 `ecommerce_recommend.user_behavior` 表，
按 `(user_id, behavior_type)` 做累计聚合，
并把结果写入：

- `stream_user_behavior_distribution`

### `user-category-preference-job.sql.template`

作用：

实时监听 `user_behavior`，通过 MySQL `product`、`category` 维表补齐品类信息，
按 `(user_id, category_id)` 聚合用户偏好分数，并写入：

- `stream_user_category_preference`

### `product-hotness-realtime-job.sql.template`

作用：

实时监听 `user_behavior`，按商品维度累计热度分数与行为次数，
并写入：

- `stream_product_hotness_realtime`

### `dwd-user-behavior-event-job.example.sql`

把 Debezium 的 `cdc.user_behavior` 标准化为：

- `dwd.user_behavior_event`

### `dwd-order-paid-event-job.example.sql`

把 `cdc.orders + cdc.order_item` 标准化为：

- `dwd.order_paid_event`

### `dwd-product-changed-event-job.example.sql`

把 Debezium 的 `cdc.product` 标准化为：

- `dwd.product_changed_event`

### `dwd-recommendation-event-job.example.sql`

把 Debezium 的 `cdc.recommendation_event` 标准化为：

- `dwd.recommendation_event`

### `user-behavior-distribution-job.example.sql`

Kafka 增强版示例：

- source topic: `dwd.user_behavior_event`
- sink topic: `dws.user_behavior_distribution`

### `user-category-preference-job.example.sql`

Kafka 增强版示例：

- source topic: `dwd.user_behavior_event`
- sink topic: `dws.user_category_preference`

### `product-hotness-realtime-job.example.sql`

Kafka 增强版示例：

- source topic: `dwd.user_behavior_event`
- source topic: `dwd.order_paid_event`
- sink topic: `dws.product_hotness_realtime`

说明：

- 该作业已把**支付事件**纳入热度加权，不再只看浏览/加购/收藏。
- 该作业会输出 **1m / 1h / 1d** 三个窗口，便于做实时秒级榜、小时榜、日榜切换。

### `recommendation-core-metrics-realtime-job.example.sql`

基于 `dwd.recommendation_event` 聚合推荐看板核心 8 指标：

- DAU
- CTR
- CVR
- GMV
- 客单价
- 复购率
- 7 日留存率
- 退款率

sink topic:

- `dws.recommendation_core_metrics_realtime`

## 推荐扩展路径

后续可继续加真实运行版：

1. 用户实时品类偏好
2. 商品实时热度
3. 用户实时标签画像
4. 订单支付事件热度加权
5. 商品维表变更事件（`dwd.product_changed_event`）
6. 多窗口（1m/1h/1d）热榜
7. 推荐核心 8 指标实时看板（`dws.recommendation_core_metrics_realtime`）

## 提交方式

请通过：

- `..\scripts\run-user-behavior-distribution-job.ps1`
- `..\scripts\run-user-category-preference-job.ps1`
- `..\scripts\run-product-hotness-realtime-job.ps1`

由脚本把数据库连接参数注入 SQL 模板后，再调用 Flink SQL Client 提交。

## 说明

如果你要做更接近真实大数据电商系统的联调，优先看 `*.example.sql`；
如果你只是本机快速验证，则继续使用 `*.sql.template` + `run-*.ps1`。
