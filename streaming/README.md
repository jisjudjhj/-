# Streaming（本机 Flink 直连 + Kafka 增强链路）

当前目录同时保留两条链路：

- **方案 A：本机 Flink CDC 直连 MySQL**
- **方案 B：MySQL CDC -> Kafka -> DWD/DWS -> Redis**

## 目标

方案 A 会直接监听 MySQL 表变更，由 Flink 实时聚合并回写到 MySQL 汇总表：

- 源表：`user_behavior`
- 汇总表：`stream_user_behavior_distribution`
- 汇总表：`stream_user_category_preference`
- 汇总表：`stream_product_hotness_realtime`

方案 B 会把实时链路升级为：

- `cdc.user_behavior`
- `cdc.orders`
- `cdc.order_item`
- `cdc.product`
- `cdc.recommendation_event`
- `dwd.user_behavior_event`
- `dwd.order_paid_event`
- `dwd.product_changed_event`
- `dwd.recommendation_event`
- `dws.user_behavior_distribution`
- `dws.user_category_preference`
- `dws.product_hotness_realtime`
- `dws.recommendation_core_metrics_realtime`
- `dws.user_behavior_distribution.dlt`
- `dws.user_category_preference.dlt`
- `dws.product_hotness_realtime.dlt`
- `dws.recommendation_core_metrics_realtime.dlt`
- 后端 Kafka Consumer -> Redis -> 管理端看板 / 推荐接口

## 前置条件

### 1. 本机安装 Flink

建议使用与项目当前约定一致的 **Flink 1.18.x**。

并设置环境变量：

```powershell
$env:FLINK_HOME="D:\flink-1.18.1"
```

或在系统环境变量中永久设置 `FLINK_HOME`。

### 2. 把所需 connector JAR 放到 `FLINK_HOME\\lib`

至少需要：

- Flink CDC MySQL connector
- Flink JDBC connector
- MySQL JDBC driver

> 注意：请使用与你本机 Flink 版本匹配的 connector 版本。

### 3. MySQL 开启 binlog

`user_behavior` 的 CDC 依赖 MySQL binlog。

建议确认 MySQL 已启用类似配置：

```ini
log_bin=mysql-bin
binlog_format=ROW
binlog_row_image=FULL
server-id=1
```

---

## 首次初始化

### 1. 创建聚合结果表

```sql
source F:\IDEAwenjian\大数据电商系统\streaming\scripts\create-streaming-sink-tables.sql
```

或手动执行里面的 SQL。

### 2. 启动本机 Flink 集群

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File F:\IDEAwenjian\大数据电商系统\streaming\scripts\start-local-flink-cluster.ps1
```

启动后可访问：

- Flink UI: [http://localhost:8081](http://localhost:8081)

### 3. 运行实时作业

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File F:\IDEAwenjian\大数据电商系统\streaming\scripts\run-user-behavior-distribution-job.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File F:\IDEAwenjian\大数据电商系统\streaming\scripts\run-user-category-preference-job.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File F:\IDEAwenjian\大数据电商系统\streaming\scripts\run-product-hotness-realtime-job.ps1
```

默认连接：

- MySQL Host: `127.0.0.1`
- Port: `3306`
- DB: `ecommerce_recommend`
- User: `root`
- Password: `root`

如果你的本地库不是这个配置，可以传参：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File F:\IDEAwenjian\大数据电商系统\streaming\scripts\run-user-behavior-distribution-job.ps1 `
  -MySqlHost 127.0.0.1 `
  -MySqlPort 3306 `
  -Database ecommerce_recommend `
  -Username root `
  -Password root
```

另外两条作业传参方式相同：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File F:\IDEAwenjian\大数据电商系统\streaming\scripts\run-user-category-preference-job.ps1 `
  -MySqlHost 127.0.0.1 `
  -MySqlPort 3306 `
  -Database ecommerce_recommend `
  -Username root `
  -Password root

powershell -NoProfile -ExecutionPolicy Bypass -File F:\IDEAwenjian\大数据电商系统\streaming\scripts\run-product-hotness-realtime-job.ps1 `
  -MySqlHost 127.0.0.1 `
  -MySqlPort 3306 `
  -Database ecommerce_recommend `
  -Username root `
  -Password root
```

---

## 停止本机 Flink 集群

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File F:\IDEAwenjian\大数据电商系统\streaming\scripts\stop-local-flink-cluster.ps1
```

---

## 当前目录说明

- `flink-jobs/`
  - 直连 MySQL 的 SQL 模板
  - Kafka 增强链路的 DWD / DWS SQL 示例
- `scripts/`
  - 本机启动、停机、提交三条实时作业的脚本
  - Kafka topic 初始化脚本
  - Debezium Connector 注册脚本
- `connectors/`
  - `user_behavior / orders / order_item / product / recommendation_event` 的 CDC Connector 配置
- `docker-compose.yml`
  - Kafka / Debezium Connect / Flink 容器化链路

---

## Kafka 增强链路的最小联调步骤

```powershell
cd F:\IDEAwenjian\大数据电商系统\streaming
docker compose up -d
.\scripts\create-topics.ps1
.\scripts\register-core-cdc-connectors.ps1
```

然后根据 `flink-jobs` 中的示例提交作业：

1. `dwd-user-behavior-event-job.example.sql`
2. `dwd-order-paid-event-job.example.sql`
3. `dwd-product-changed-event-job.example.sql`
4. `dwd-recommendation-event-job.example.sql`
5. `user-behavior-distribution-job.example.sql`
6. `user-category-preference-job.example.sql`
7. `product-hotness-realtime-job.example.sql`
8. `recommendation-core-metrics-realtime-job.example.sql`

后端启用：

```powershell
STREAM_REALTIME_ENABLED=true
STREAM_KAFKA_ENABLED=true
```

即可通过：

- `/api/admin/stream/status`
- `/api/admin/stream/overview`
- `/api/admin/stream/users/{userId}/snapshot`
- `/api/admin/stream/recommendation-kpi/realtime`

查看实时效果。

---

## 轻量链路特点

- 不需要 Docker
- 不需要 Debezium Connect
- 不需要注册 CDC 脚本
- 直接监听 `user_behavior` 表变更
- 更适合本机联调和快速验证实时链路

---

## 当前运行流程

1. 执行 `create-streaming-sink-tables.sql`
2. 设置 `FLINK_HOME`
3. 启动本机 Flink 集群
4. 提交一个或多个实时作业脚本

---

## 说明

如果你只是本机快速验证，用方案 A。

如果你要更接近真实大数据电商系统，就走方案 B，并优先使用：

- `create-topics.ps1`
- `register-core-cdc-connectors.ps1`
- `dwd-* / dws-*` SQL 示例
