# Docker Deployment Notes

这套部署文件按“小内存服务器”做了收敛，并保留两种部署方式：

- 全容器模式：`backend`、`admin-frontend`、`redis`、`rabbitmq`
- 混合模式：`backend` 跑宿主机，`admin-frontend`、`redis`、`rabbitmq` 跑 Docker
- 演示时临时拉起：`zookeeper`、`kafka`、`kafka-ui`、`connect`、`jobmanager`、`taskmanager`

## 1. 环境变量

复制并修改：

```bash
cp deploy/server.env.example deploy/server.env
```

当前模板默认复用宿主机已有的 MySQL：

- 数据库：`111111`
- 用户名：`111111`
- 密码：`root`

## 2. 全容器模式

```bash
docker compose --env-file deploy/server.env -f deploy/docker-compose.server.yml up -d redis backend admin-frontend
```

## 3. 启动 RabbitMQ

```bash
docker compose --env-file deploy/server.env -f deploy/docker-compose.server.yml --profile mq up -d rabbitmq
```

同时把 `MQ_ENABLED=true` 写入 `deploy/server.env`，然后重启后端：

```bash
docker compose --env-file deploy/server.env -f deploy/docker-compose.server.yml up -d backend
```

## 4. 启动 Kafka / Flink 增强链路

```bash
docker compose --env-file deploy/server.env -f deploy/docker-compose.server.yml --profile streaming up -d
```

再把下面开关打开：

```text
STREAM_KAFKA_ENABLED=true
STREAM_REALTIME_ENABLED=true
```

然后重启后端：

```bash
docker compose --env-file deploy/server.env -f deploy/docker-compose.server.yml up -d backend
```

## 5. 混合模式

如果宿主机 MySQL 只允许 `localhost` 连接，推荐这样跑：

1. 只启动 Docker 服务：

```bash
docker compose --env-file deploy/server.env -f deploy/docker-compose.runtime.yml up -d redis rabbitmq admin-frontend
```

2. 把 `deploy/ecommerce-backend.service` 放到：

```text
/etc/systemd/system/ecommerce-backend.service
```

3. 重新加载并启动：

```bash
systemctl daemon-reload
systemctl enable --now ecommerce-backend
```

4. 安装健康检查脚本与定时器：

```bash
install -m 755 deploy/ecommerce-backend-healthcheck.sh /opt/ecommerce/scripts/ecommerce-backend-healthcheck.sh
cp deploy/ecommerce-backend-healthcheck.service /etc/systemd/system/
cp deploy/ecommerce-backend-healthcheck.timer /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now ecommerce-backend-healthcheck.timer
```

## 6. 宿主机 Nginx

宿主机 Nginx 继续监听 `80`，把请求反代给容器：

- `/` -> `127.0.0.1:8088`
- `/api` -> `127.0.0.1:8080`
- `/uploads` -> `127.0.0.1:8080`
- `/ws` -> `127.0.0.1:8080`

配置参考文件：

```text
deploy/nginx-ecommerce.conf
deploy/nginx-bt-site.conf
```

## 7. Python 说明

当前仓库已包含 `backend/python_analytics/`，可通过根目录脚本触发离线分析：

```bash
npm run analytics:run
```

生产部署时建议把 Python analytics 作为独立 worker 或定时任务运行，并复用后端相同的数据库环境变量：

- `ANALYTICS_DB_HOST`
- `ANALYTICS_DB_PORT`
- `ANALYTICS_DB_USERNAME`
- `ANALYTICS_DB_PASSWORD`
- `ANALYTICS_DB_NAME`

Python 调度器已写入 `analytics_job_log`，并通过 MySQL `GET_LOCK` 做同一任务/日期的互斥保护，避免手动触发和定时触发撞车。

## 8. 说明

- `docker-compose.runtime.yml` 默认把端口绑定到 `127.0.0.1`，依赖宿主机 Nginx 做公网入口。
- 当前混合部署建议：电商后端只保留 systemd 服务，`docker-compose.runtime.yml` 仅负责 `admin-frontend`、`redis`、`rabbitmq` 等辅助容器。
- `ecommerce-backend.service` 已加入 `StartLimitIntervalSec=15min`、`StartLimitBurst=3`、`RestartSec=30`，用于避免失败时无限狂重启。
- `ecommerce-backend-healthcheck.timer` 每分钟检查一次 `http://127.0.0.1:8080/ws/info`，连续 3 次失败才允许重启，并带 30 分钟冷却和 6 小时内最多 2 次自动重启的限制。
- 2 GB 左右内存的机器不建议常驻启动 Kafka/Flink。
