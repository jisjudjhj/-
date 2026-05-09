# 大数据电商系统

这是一个面向演示和企业化改造的电商系统，覆盖用户小程序、商家端、管理端、后端服务、推荐链路和离线分析任务。

## 模块入口

- 用户小程序：`user-miniprogram---2`
- 管理端 PC：`management-pc`
- 商家端 PC：`merchant-pc`
- 后端服务：`backend`
- 数据分析脚本：`backend/python`
- 小程序巡检脚本：`scripts/audit-mini-transaction-recommendation.ps1`

## 分层文档

- [本地启动](docs/local-start.md)
- [演示流程](docs/demo-flow.md)
- [生产部署](docs/production-deploy.md)
- [数据分析任务](docs/analytics-jobs.md)
- [推荐链路说明](docs/recommendation-flow.md)

## 当前重点

- 小程序端推荐、搜索、购物车、结算、订单完成页的真实交易体验。
- 推荐曝光、点击、加购、下单、支付、退款的归因闭环。
- 管理端分析页的任务运行、指标快照、质量告警和算法效果追踪。
- 后端推荐服务的召回、排序、重排、负反馈、缓存和归因分层。
