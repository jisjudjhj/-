package com.ecommerce.service;

import java.util.List;
import java.util.Map;

/**
 * 大数据分析服务 — 提供用户行为漏斗、RFM 分群、关联规则挖掘、
 * 留存分析、销售趋势预测等数据分析能力。
 */
public interface DataAnalysisService {

    /** 行为漏斗分析: view → cart → favorite → purchase 各阶段转化率 */
    Map<String, Object> funnelAnalysis();

    /** RFM 用户分群: Recency(最近消费)、Frequency(消费频率)、Monetary(消费金额) */
    Map<String, Object> rfmSegmentation();

    /** 关联规则挖掘 (购物篮分析): 商品共购关系、支持度、置信度、提升度 */
    Map<String, Object> associationRules(int minSupport, double minConfidence);

    /** 用户留存分析: 按注册月份分组，追踪后续月份活跃率 */
    Map<String, Object> retentionAnalysis();

    /** 销售趋势分析: 近30天每日销售额 + 7日移动平均线 + 同比环比 */
    Map<String, Object> salesTrendAnalysis();

    /** 用户活跃度热力图: 按小时/星期统计行为密度 */
    Map<String, Object> activityHeatmap();

    /** 综合概览: 汇总所有分析指标的摘要 */
    Map<String, Object> analysisSummary();

    /** 分析链路健康度: 评估核心分析表的新鲜度、覆盖度与可用性 */
    Map<String, Object> analysisHealthScore();
}
