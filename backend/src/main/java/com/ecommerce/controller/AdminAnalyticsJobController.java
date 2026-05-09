package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.Result;
import com.ecommerce.entity.AnalyticsJobLog;
import com.ecommerce.mapper.AnalyticsJobLogMapper;
import com.ecommerce.scheduler.PythonAnalyticsScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/analysis/jobs")
public class AdminAnalyticsJobController {

    @Autowired
    private AnalyticsJobLogMapper analyticsJobLogMapper;

    @Autowired
    private PythonAnalyticsScheduler pythonAnalyticsScheduler;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @GetMapping
    public Result<?> list(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "10") int size,
                          @RequestParam(required = false) String jobName,
                          @RequestParam(required = false) String status) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        LambdaQueryWrapper<AnalyticsJobLog> query = new LambdaQueryWrapper<AnalyticsJobLog>()
                .orderByDesc(AnalyticsJobLog::getStartTime)
                .orderByDesc(AnalyticsJobLog::getId);
        if (StringUtils.hasText(jobName)) {
            query.eq(AnalyticsJobLog::getJobName, jobName.trim());
        }
        if (StringUtils.hasText(status)) {
            query.eq(AnalyticsJobLog::getStatus, status.trim());
        }
        IPage<AnalyticsJobLog> pageData = analyticsJobLogMapper.selectPage(new Page<>(safePage, safeSize), query);
        List<Map<String, Object>> records = pageData.getRecords().stream()
                .map(this::buildJobPayload)
                .collect(Collectors.toList());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("records", records);
        payload.put("total", pageData.getTotal());
        payload.put("current", pageData.getCurrent());
        payload.put("pages", pageData.getPages());
        payload.put("size", pageData.getSize());
        payload.put("running", pythonAnalyticsScheduler.isRunning());
        return Result.success(payload);
    }

    @PostMapping("/trigger")
    public Result<?> trigger(@RequestBody(required = false) Map<String, Object> body) {
        String jobs = body == null ? "analytics,kmeans,recommendation" : String.valueOf(body.getOrDefault("jobs", "analytics,kmeans,recommendation"));
        String taskName = body == null ? "manual_python_analytics" : String.valueOf(body.getOrDefault("taskName", "manual_python_analytics"));
        boolean includeKmeansParams = jobs != null && jobs.toLowerCase().contains("kmeans");
        return Result.success(pythonAnalyticsScheduler.triggerManualCycle(taskName, jobs, includeKmeansParams));
    }

    @GetMapping("/recommendation-metrics")
    public Result<?> recommendationMetrics(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) String statDate) {
        if (jdbcTemplate == null) {
            return Result.success(emptyPage(page, size));
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int offset = (safePage - 1) * safeSize;
        String where = StringUtils.hasText(statDate) ? " WHERE stat_date = ? " : "";
        Object[] args = StringUtils.hasText(statDate)
                ? new Object[]{statDate.trim(), safeSize, offset}
                : new Object[]{safeSize, offset};
        Long total = StringUtils.hasText(statDate)
                ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM analytics_recommendation_metric_daily WHERE stat_date = ?", Long.class, statDate.trim())
                : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM analytics_recommendation_metric_daily", Long.class);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT stat_date AS statDate, scene, algorithm, segment_code AS segmentCode, " +
                        "exposure_count AS exposureCount, click_count AS clickCount, cart_count AS cartCount, " +
                        "paid_order_count AS paidOrderCount, refund_count AS refundCount, gmv, refund_amount AS refundAmount, " +
                        "ctr, cvr, cart_rate AS cartRate, refund_rate AS refundRate, model_version AS modelVersion " +
                        "FROM analytics_recommendation_metric_daily " + where +
                        "ORDER BY stat_date DESC, exposure_count DESC LIMIT ? OFFSET ?",
                args);
        return Result.success(pagePayload(records, total == null ? 0 : total, safePage, safeSize));
    }

    @GetMapping("/quality-alerts")
    public Result<?> qualityAlerts(@RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "20") int size,
                                   @RequestParam(required = false) String status) {
        if (jdbcTemplate == null) {
            return Result.success(emptyPage(page, size));
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int offset = (safePage - 1) * safeSize;
        boolean hasStatus = StringUtils.hasText(status);
        Long total = hasStatus
                ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM analytics_data_quality_alert WHERE status = ?", Long.class, status.trim())
                : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM analytics_data_quality_alert", Long.class);
        Object[] args = hasStatus ? new Object[]{status.trim(), safeSize, offset} : new Object[]{safeSize, offset};
        String where = hasStatus ? " WHERE status = ? " : "";
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT id, stat_date AS statDate, check_code AS checkCode, severity, status, " +
                        "actual_value AS actualValue, threshold_value AS thresholdValue, message, detail_json AS detailJson, " +
                        "create_time AS createTime, update_time AS updateTime " +
                        "FROM analytics_data_quality_alert " + where +
                        "ORDER BY stat_date DESC, FIELD(severity, 'critical', 'warning', 'info'), id DESC LIMIT ? OFFSET ?",
                args);
        return Result.success(pagePayload(records, total == null ? 0 : total, safePage, safeSize));
    }

    @PostMapping("/quality-alerts/resolve")
    public Result<?> resolveQualityAlerts(@RequestBody(required = false) Map<String, Object> body) {
        if (jdbcTemplate == null || body == null) {
            return Result.success(Collections.singletonMap("updated", 0));
        }
        Object rawId = body.get("id");
        if (rawId == null) {
            return Result.success(Collections.singletonMap("updated", 0));
        }
        int updated = jdbcTemplate.update(
                "UPDATE analytics_data_quality_alert SET status='resolved', update_time=NOW() WHERE id=?",
                rawId);
        return Result.success(Collections.singletonMap("updated", updated));
    }

    private Map<String, Object> buildJobPayload(AnalyticsJobLog log) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", log.getId());
        payload.put("jobName", log.getJobName());
        payload.put("batchNo", log.getBatchNo());
        payload.put("jobType", log.getJobType());
        payload.put("status", log.getStatus());
        payload.put("snapshotDate", log.getSnapshotDate());
        payload.put("processedCount", log.getProcessedCount());
        payload.put("outputCount", log.getOutputCount());
        payload.put("resultSummary", log.getResultSummary());
        payload.put("errorMessage", log.getErrorMessage());
        payload.put("startTime", log.getStartTime());
        payload.put("endTime", log.getEndTime());
        return payload;
    }

    private Map<String, Object> emptyPage(int page, int size) {
        return pagePayload(Collections.emptyList(), 0, Math.max(1, page), Math.max(1, size));
    }

    private Map<String, Object> pagePayload(List<Map<String, Object>> records, long total, int page, int size) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("records", records == null ? Collections.emptyList() : records);
        payload.put("total", total);
        payload.put("current", page);
        payload.put("pages", size <= 0 ? 0 : (long) Math.ceil(total / (double) size));
        payload.put("size", size);
        return payload;
    }
}
