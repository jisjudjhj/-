Param(
    [string]$KafkaContainer = "ecommerce-kafka",
    [string]$BootstrapServer = "localhost:9092"
)

$ErrorActionPreference = "Stop"

$topics = @(
    "cdc.user_behavior",
    "cdc.orders",
    "cdc.order_item",
    "cdc.product",
    "cdc.recommendation_event",
    "dwd.user_behavior_event",
    "dwd.order_paid_event",
    "dwd.product_changed_event",
    "dwd.recommendation_event",
    "dws.user_behavior_distribution",
    "dws.user_category_preference",
    "dws.product_hotness_realtime",
    "dws.recommendation_core_metrics_realtime",
    "dws.user_behavior_distribution.dlt",
    "dws.user_category_preference.dlt",
    "dws.product_hotness_realtime.dlt",
    "dws.recommendation_core_metrics_realtime.dlt"
)

$containerNames = docker ps --format "{{.Names}}"
if (-not ($containerNames -contains $KafkaContainer)) {
    throw "Kafka 容器 '$KafkaContainer' 未运行，请先在 streaming 目录执行 docker compose up -d。"
}

foreach ($topic in $topics) {
    Write-Host "==> creating topic: $topic"
    docker exec $KafkaContainer kafka-topics `
        --bootstrap-server $BootstrapServer `
        --create `
        --if-not-exists `
        --topic $topic `
        --partitions 3 `
        --replication-factor 1
}

Write-Host ""
Write-Host "==> topic list"
docker exec $KafkaContainer kafka-topics --bootstrap-server $BootstrapServer --list
