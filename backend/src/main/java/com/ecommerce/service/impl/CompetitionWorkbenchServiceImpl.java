package com.ecommerce.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectListing;
import com.ecommerce.common.BusinessException;
import com.ecommerce.dto.CompetitionArtifactsTriggerDTO;
import com.ecommerce.service.CompetitionWorkbenchService;
import com.ecommerce.service.ManagementWorkbenchRealtimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.StreamUtils;

import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CompetitionWorkbenchServiceImpl implements CompetitionWorkbenchService {

    private static final Logger log = LoggerFactory.getLogger(CompetitionWorkbenchServiceImpl.class);

    private static final Pattern SNAPSHOT_DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final int MAX_LIST_KEYS = 200;
    private static final List<String> ARTIFACT_NAMES = Arrays.asList(
            "ads_competition_summary.json",
            "ads_ai_brief.json",
            "dws_user_value_bands.json",
            "dws_product_heat.json",
            "dws_search_keywords.json",
            "ods_layer_overview.json",
            "dwd_layer_overview.json"
    );

    private static final String DEFAULT_OUTPUT_MODE = "oss";
    private static final String DEFAULT_SPARK_MASTER = "local[*]";
    private static final int DEFAULT_SHUFFLE_PARTITIONS = 4;
    private static final int DEFAULT_BEHAVIOR_WINDOW_DAYS = 30;
    private static final int DEFAULT_ORDER_WINDOW_DAYS = 90;

    @Value("${aliyun.oss.endpoint:}")
    private String endpoint;

    @Value("${aliyun.oss.access-key-id:}")
    private String accessKeyId;

    @Value("${aliyun.oss.access-key-secret:}")
    private String accessKeySecret;

    @Value("${aliyun.oss.bucket-name:}")
    private String bucketName;

    @Value("${aliyun.oss.url-prefix:}")
    private String urlPrefix;

    @Value("${analytics.competition.oss-prefix:competition/bigdata}")
    private String competitionOssPrefix;

    @Value("${analytics.competition.artifact-lookback-days:14}")
    private int defaultLookbackDays;

    @Value("${analytics.competition.runner.shell:powershell}")
    private String competitionRunnerShell;

    @Value("${analytics.competition.runner.script-path:}")
    private String competitionRunnerScriptPath;

    private volatile OSS ossClient;

    private final AtomicBoolean competitionLaunchInProgress = new AtomicBoolean(false);
    private volatile Map<String, Object> lastCompetitionLaunch = Collections.emptyMap();

    @Autowired
    private ManagementWorkbenchRealtimeService managementWorkbenchRealtimeService;

    @Override
    public Map<String, Object> getWorkbench(String snapshotDate, int lookbackDays) {
        Map<String, Object> payload = new LinkedHashMap<>();
        int safeLookbackDays = normalizeLookbackDays(lookbackDays);
        String baseUrl = resolveBaseUrl();
        String objectPrefix = normalizePrefix(competitionOssPrefix);

        payload.put("available", false);
        payload.put("dataSource", "oss");
        payload.put("artifactPrefix", objectPrefix);
        payload.put("baseUrl", baseUrl);
        payload.put("requestedSnapshotDate", trimToNull(snapshotDate));
        payload.put("lookbackDays", safeLookbackDays);
        payload.put("fetchedAt", LocalDateTime.now());

        if (!isOssConfigured()) {
            payload.put("message", "OSS 未完成配置，暂时无法读取比赛产物。");
            payload.put("artifactLinks", Collections.emptyList());
            return payload;
        }

        String resolvedSnapshotDate = resolveSnapshotDate(trimToNull(snapshotDate), safeLookbackDays);
        if (!StringUtils.hasText(resolvedSnapshotDate)) {
            payload.put("message", "OSS 中暂未找到比赛产物，请先运行比赛脚本。");
            payload.put("artifactLinks", Collections.emptyList());
            return payload;
        }

        Map<String, Object> summary = readJsonArtifact(resolvedSnapshotDate, "ads_competition_summary.json");
        if (summary.isEmpty()) {
            payload.put("message", "已找到快照目录，但缺少 ads_competition_summary.json。");
            payload.put("artifactLinks", buildArtifactLinks(resolvedSnapshotDate, baseUrl, objectPrefix));
            return payload;
        }

        Map<String, Object> aiBrief = readJsonArtifact(resolvedSnapshotDate, "ads_ai_brief.json");
        Map<String, Object> valueBandArtifact = readJsonArtifact(resolvedSnapshotDate, "dws_user_value_bands.json");
        Map<String, Object> productHeatArtifact = readJsonArtifact(resolvedSnapshotDate, "dws_product_heat.json");
        Map<String, Object> searchKeywordArtifact = readJsonArtifact(resolvedSnapshotDate, "dws_search_keywords.json");

        List<Map<String, Object>> userValueBands = extractRecordList(valueBandArtifact);
        if (userValueBands.isEmpty()) {
            userValueBands = toMapList(summary.get("userValueBands"));
        }

        List<Map<String, Object>> topHotProducts = extractRecordList(productHeatArtifact);
        if (topHotProducts.isEmpty()) {
            topHotProducts = toMapList(summary.get("topHotProducts"));
        }

        List<Map<String, Object>> topSearchKeywords = extractRecordList(searchKeywordArtifact);
        if (topSearchKeywords.isEmpty()) {
            topSearchKeywords = toMapList(summary.get("topSearchKeywords"));
        }

        payload.put("available", true);
        payload.put("snapshotDate", resolvedSnapshotDate);
        payload.put("summary", summary);
        payload.put("headlineMetrics", safeMap(summary.get("headlineMetrics")));
        payload.put("warehouseMetrics", safeMap(summary.get("warehouseMetrics")));
        payload.put("judgeTakeaways", toStringList(summary.get("judgeTakeaways")));
        payload.put("recommendationScenes", toMapList(summary.get("recommendationScenes")));
        payload.put("projectTitleSuggestion", stringValue(summary.get("projectTitleSuggestion")));
        payload.put("architectureNarrative", stringValue(summary.get("architectureNarrative")));
        payload.put("demoCommands", toStringList(summary.get("demoCommands")));
        payload.put("aiBrief", aiBrief);
        payload.put("userValueBands", userValueBands);
        payload.put("topHotProducts", topHotProducts);
        payload.put("topSearchKeywords", topSearchKeywords);
        payload.put("artifactLinks", buildArtifactLinks(resolvedSnapshotDate, baseUrl, objectPrefix));
        payload.put("message", "已从 OSS 读取最新比赛产物。");
        return payload;
    }

    @Override
    public Map<String, Object> triggerCompetitionArtifacts(CompetitionArtifactsTriggerDTO request) {
        Path scriptPath = resolveCompetitionScriptPath();
        if (scriptPath == null || !Files.exists(scriptPath)) {
            throw new BusinessException(500, "未能找到竞赛大数据产物脚本，请先确认 scripts/run-competition-bigdata.ps1 可用。");
        }

        if (!competitionLaunchInProgress.compareAndSet(false, true)) {
            throw new BusinessException(409, "竞赛产物刷新正在进行，请稍候再试。");
        }

        String launchId = "competition_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        CompetitionLaunchCommand command = buildCompetitionLaunchCommand(request, scriptPath, launchId);
        Map<String, Object> acceptedPayload = buildAcceptedCompetitionPayload(command);
        lastCompetitionLaunch = acceptedPayload;

        try {
            CompletableFuture.runAsync(() -> executeCompetitionLaunch(command));
        } catch (Exception ex) {
            competitionLaunchInProgress.set(false);
            throw new BusinessException(500, "无法启动竞赛产物刷新脚本: " + ex.getMessage());
        }
        managementWorkbenchRealtimeService.notifyAdminAnalysisRefresh(
                "competition-artifacts-accepted",
                new LinkedHashMap<>(acceptedPayload)
        );
        return acceptedPayload;
    }

    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    private boolean isOssConfigured() {
        return StringUtils.hasText(endpoint)
                && StringUtils.hasText(accessKeyId)
                && StringUtils.hasText(accessKeySecret)
                && StringUtils.hasText(bucketName);
    }

    private OSS getOssClient() {
        if (!isOssConfigured()) {
            return null;
        }
        if (ossClient == null) {
            synchronized (this) {
                if (ossClient == null) {
                    ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
                }
            }
        }
        return ossClient;
    }

    private String resolveSnapshotDate(String requestedSnapshotDate, int lookbackDays) {
        if (StringUtils.hasText(requestedSnapshotDate)) {
            return requestedSnapshotDate;
        }

        List<String> candidates = listSnapshotDates();
        if (candidates.isEmpty()) {
            return null;
        }

        LocalDate threshold = LocalDate.now().minusDays(Math.max(lookbackDays - 1, 0));
        for (String candidate : candidates) {
            try {
                LocalDate date = LocalDate.parse(candidate);
                if (!date.isBefore(threshold)) {
                    return candidate;
                }
            } catch (DateTimeParseException ignored) {
                return candidate;
            }
        }
        return candidates.get(0);
    }

    private List<String> listSnapshotDates() {
        OSS client = getOssClient();
        if (client == null) {
            return Collections.emptyList();
        }

        String prefix = normalizePrefix(competitionOssPrefix) + "/";
        Set<String> snapshotDates = new LinkedHashSet<>();
        String marker = null;

        do {
            ListObjectsRequest request = new ListObjectsRequest(bucketName)
                    .withPrefix(prefix)
                    .withDelimiter("/")
                    .withMaxKeys(MAX_LIST_KEYS);
            if (StringUtils.hasText(marker)) {
                request.setMarker(marker);
            }

            ObjectListing listing = client.listObjects(request);
            for (String commonPrefix : listing.getCommonPrefixes()) {
                String snapshotDate = extractSnapshotDate(commonPrefix, prefix);
                if (snapshotDate != null) {
                    snapshotDates.add(snapshotDate);
                }
            }
            marker = listing.isTruncated() ? listing.getNextMarker() : null;
        } while (StringUtils.hasText(marker));

        List<String> result = new ArrayList<>(snapshotDates);
        result.sort(Comparator.reverseOrder());
        return result;
    }

    private CompetitionLaunchCommand buildCompetitionLaunchCommand(CompetitionArtifactsTriggerDTO request,
                                                                  Path scriptPath,
                                                                  String launchId) {
        String snapshotDate = normalizeCompetitionSnapshotDate(request == null ? null : request.getSnapshotDate());
        String outputMode = StringUtils.hasText(request == null ? null : request.getOutputMode())
                ? request.getOutputMode().trim() : DEFAULT_OUTPUT_MODE;
        String ossPrefix = StringUtils.hasText(request == null ? null : request.getOssPrefix())
                ? request.getOssPrefix().trim() : competitionOssPrefix;
        String sparkMaster = StringUtils.hasText(request == null ? null : request.getSparkMaster())
                ? request.getSparkMaster().trim() : DEFAULT_SPARK_MASTER;
        int shufflePartitions = safeInt(request == null ? null : request.getShufflePartitions(), DEFAULT_SHUFFLE_PARTITIONS);
        int behaviorWindowDays = safeInt(request == null ? null : request.getBehaviorWindowDays(), DEFAULT_BEHAVIOR_WINDOW_DAYS);
        int orderWindowDays = safeInt(request == null ? null : request.getOrderWindowDays(), DEFAULT_ORDER_WINDOW_DAYS);
        boolean skipAiBrief = Boolean.TRUE.equals(request == null ? null : request.getSkipAiBrief());

        return new CompetitionLaunchCommand(
                launchId,
                scriptPath,
                snapshotDate,
                outputMode,
                ossPrefix,
                sparkMaster,
                shufflePartitions,
                behaviorWindowDays,
                orderWindowDays,
                skipAiBrief
        );
    }

    private Map<String, Object> buildAcceptedCompetitionPayload(CompetitionLaunchCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("launchId", command.launchId);
        payload.put("status", "accepted");
        payload.put("snapshotDate", command.snapshotDate);
        payload.put("outputMode", command.outputMode);
        payload.put("ossPrefix", command.ossPrefix);
        payload.put("sparkMaster", command.sparkMaster);
        payload.put("shufflePartitions", command.shufflePartitions);
        payload.put("behaviorWindowDays", command.behaviorWindowDays);
        payload.put("orderWindowDays", command.orderWindowDays);
        payload.put("skipAiBrief", command.skipAiBrief);
        payload.put("triggeredAt", LocalDateTime.now());
        payload.put("shellCommand", competitionRunnerShell);
        payload.put("scriptPath", command.scriptPath.toString());
        payload.put("commandPreview", buildCommandPreview(buildCompetitionProcessCommand(command)));
        return payload;
    }

    private void executeCompetitionLaunch(CompetitionLaunchCommand command) {
        List<String> processCommand = buildCompetitionProcessCommand(command);
        Map<String, Object> runningState = new LinkedHashMap<>(lastCompetitionLaunch);
        runningState.put("status", "running");
        runningState.put("startedAt", LocalDateTime.now());
        lastCompetitionLaunch = runningState;
        managementWorkbenchRealtimeService.notifyAdminAnalysisRefresh(
                "competition-artifacts-running",
                new LinkedHashMap<>(runningState)
        );

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(processCommand);
            processBuilder.redirectErrorStream(true);
            processBuilder.directory(command.scriptPath.getParent().toFile());

            log.info("Starting competition pipeline manually: launchId={}, command={}",
                    command.launchId, buildCommandPreview(processCommand));

            Process process = processBuilder.start();
            drainCompetitionProcessOutput(process, command.launchId);
            int exitCode = process.waitFor();

            Map<String, Object> finishState = new LinkedHashMap<>(lastCompetitionLaunch);
            finishState.put("status", exitCode == 0 ? "success" : "failed");
            finishState.put("exitCode", exitCode);
            finishState.put("finishedAt", LocalDateTime.now());
            lastCompetitionLaunch = finishState;
            managementWorkbenchRealtimeService.notifyAdminAnalysisRefresh(
                    "competition-artifacts-finished",
                    new LinkedHashMap<>(finishState)
            );

            if (exitCode == 0) {
                log.info("Competition pipeline finished successfully: launchId={}", command.launchId);
            } else {
                log.warn("Competition pipeline finished with exit code: launchId={}, exitCode={}",
                        command.launchId, exitCode);
            }
        } catch (Exception ex) {
            Map<String, Object> failureState = new LinkedHashMap<>(lastCompetitionLaunch);
            failureState.put("status", "failed");
            failureState.put("finishedAt", LocalDateTime.now());
            failureState.put("errorMessage", ex.getMessage());
            lastCompetitionLaunch = failureState;
            managementWorkbenchRealtimeService.notifyAdminAnalysisRefresh(
                    "competition-artifacts-finished",
                    new LinkedHashMap<>(failureState)
            );
            log.error("Manual competition pipeline execution failed: launchId={}, message={}",
                    command.launchId, ex.getMessage(), ex);
        } finally {
            competitionLaunchInProgress.set(false);
        }
    }

    private void drainCompetitionProcessOutput(Process process, String launchId) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[competition:{}] {}", launchId, line);
            }
        } finally {
            reader.close();
        }
    }

    private List<String> buildCompetitionProcessCommand(CompetitionLaunchCommand command) {
        List<String> args = new ArrayList<>();
        args.add(competitionRunnerShell);
        args.add("-ExecutionPolicy");
        args.add("Bypass");
        args.add("-File");
        args.add(command.scriptPath.toString());
        args.add("-SnapshotDate");
        args.add(command.snapshotDate);
        args.add("-OutputMode");
        args.add(command.outputMode);
        args.add("-OssPrefix");
        args.add(command.ossPrefix);
        args.add("-SparkMaster");
        args.add(command.sparkMaster);
        args.add("-ShufflePartitions");
        args.add(String.valueOf(command.shufflePartitions));
        args.add("-BehaviorWindowDays");
        args.add(String.valueOf(command.behaviorWindowDays));
        args.add("-OrderWindowDays");
        args.add(String.valueOf(command.orderWindowDays));
        if (command.skipAiBrief) {
            args.add("-SkipAiBrief");
        }
        return args;
    }

    private String buildCommandPreview(List<String> command) {
        return command.stream()
                .map(token -> token.contains(" ") ? "\"" + token + "\"" : token)
                .collect(Collectors.joining(" "));
    }

    private Path resolveCompetitionScriptPath() {
        List<Path> candidates = new ArrayList<>();
        if (StringUtils.hasText(competitionRunnerScriptPath)) {
            candidates.add(resolvePathCandidate(competitionRunnerScriptPath));
        }

        Path userDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        candidates.add(userDir.resolve("../scripts/run-competition-bigdata.ps1").normalize());
        candidates.add(userDir.resolve("scripts/run-competition-bigdata.ps1").normalize());
        if (userDir.getParent() != null) {
            candidates.add(userDir.getParent().resolve("scripts/run-competition-bigdata.ps1").normalize());
        }

        for (Path candidate : candidates) {
            if (candidate != null && Files.exists(candidate)) {
                return candidate;
            }
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private Path resolvePathCandidate(String rawPath) {
        Path direct = Paths.get(rawPath);
        if (direct.isAbsolute()) {
            return direct.normalize();
        }
        Path userDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        return userDir.resolve(rawPath).normalize();
    }

    private String normalizeCompetitionSnapshotDate(String snapshotDate) {
        if (!StringUtils.hasText(snapshotDate)) {
            return LocalDate.now().toString();
        }
        try {
            return LocalDate.parse(snapshotDate.trim()).toString();
        } catch (DateTimeParseException ex) {
            throw new BusinessException(400, "日期格式错误，请使用 yyyy-MM-dd");
        }
    }

    private int safeInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static final class CompetitionLaunchCommand {

        private final String launchId;
        private final Path scriptPath;
        private final String snapshotDate;
        private final String outputMode;
        private final String ossPrefix;
        private final String sparkMaster;
        private final int shufflePartitions;
        private final int behaviorWindowDays;
        private final int orderWindowDays;
        private final boolean skipAiBrief;

        private CompetitionLaunchCommand(String launchId,
                                         Path scriptPath,
                                         String snapshotDate,
                                         String outputMode,
                                         String ossPrefix,
                                         String sparkMaster,
                                         int shufflePartitions,
                                         int behaviorWindowDays,
                                         int orderWindowDays,
                                         boolean skipAiBrief) {
            this.launchId = launchId;
            this.scriptPath = scriptPath;
            this.snapshotDate = snapshotDate;
            this.outputMode = outputMode;
            this.ossPrefix = ossPrefix;
            this.sparkMaster = sparkMaster;
            this.shufflePartitions = shufflePartitions;
            this.behaviorWindowDays = behaviorWindowDays;
            this.orderWindowDays = orderWindowDays;
            this.skipAiBrief = skipAiBrief;
        }
    }

    private Map<String, Object> readJsonArtifact(String snapshotDate, String artifactName) {
        OSS client = getOssClient();
        if (client == null || !StringUtils.hasText(snapshotDate)) {
            return Collections.emptyMap();
        }

        String objectKey = buildObjectKey(snapshotDate, artifactName);
        OSSObject object = null;
        try {
            object = client.getObject(bucketName, objectKey);
            byte[] bytes = StreamUtils.copyToByteArray(object.getObjectContent());
            if (bytes.length == 0) {
                return Collections.emptyMap();
            }
            String content = new String(bytes, StandardCharsets.UTF_8);
            JSONObject jsonObject = JSON.parseObject(content);
            return jsonObject == null ? Collections.emptyMap() : new LinkedHashMap<>(jsonObject);
        } catch (OSSException exception) {
            log.debug("Competition artifact not found on OSS: {}", objectKey);
            return Collections.emptyMap();
        } catch (IOException exception) {
            log.warn("Failed to read competition artifact from OSS: {}", objectKey, exception);
            return Collections.emptyMap();
        } finally {
            if (object != null) {
                try {
                    object.close();
                } catch (IOException ignored) {
                    // no-op
                }
            }
        }
    }

    private List<Map<String, Object>> buildArtifactLinks(String snapshotDate, String baseUrl, String objectPrefix) {
        if (!StringUtils.hasText(snapshotDate) || !StringUtils.hasText(baseUrl)) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> links = new ArrayList<>();
        for (String artifactName : ARTIFACT_NAMES) {
            Map<String, Object> item = new LinkedHashMap<>();
            String objectKey = objectPrefix + "/" + snapshotDate + "/" + artifactName;
            item.put("name", artifactName);
            item.put("label", resolveArtifactLabel(artifactName));
            item.put("objectKey", objectKey);
            item.put("url", baseUrl + "/" + objectKey);
            links.add(item);
        }
        return links;
    }

    private String buildObjectKey(String snapshotDate, String artifactName) {
        return normalizePrefix(competitionOssPrefix) + "/" + snapshotDate + "/" + artifactName;
    }

    private String resolveBaseUrl() {
        String normalized = trimToNull(urlPrefix);
        if (normalized != null) {
            return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        }
        if (!StringUtils.hasText(bucketName) || !StringUtils.hasText(endpoint)) {
            return null;
        }
        String endpointHost = endpoint.contains("://")
                ? endpoint.substring(endpoint.indexOf("://") + 3)
                : endpoint;
        return "https://" + bucketName + "." + endpointHost;
    }

    private String resolveArtifactLabel(String artifactName) {
        if ("ads_competition_summary.json".equals(artifactName)) {
            return "比赛总摘要";
        }
        if ("ads_ai_brief.json".equals(artifactName)) {
            return "AI 运营摘要";
        }
        if ("dws_user_value_bands.json".equals(artifactName)) {
            return "用户价值分层";
        }
        if ("dws_product_heat.json".equals(artifactName)) {
            return "商品热度主题";
        }
        if ("dws_search_keywords.json".equals(artifactName)) {
            return "搜索热词主题";
        }
        if ("ods_layer_overview.json".equals(artifactName)) {
            return "ODS 原始层概览";
        }
        if ("dwd_layer_overview.json".equals(artifactName)) {
            return "DWD 明细层概览";
        }
        return artifactName;
    }

    private List<Map<String, Object>> extractRecordList(Map<String, Object> artifact) {
        return toMapList(artifact.get("records"));
    }

    private List<Map<String, Object>> toMapList(Object rawValue) {
        if (!(rawValue instanceof List)) {
            return Collections.emptyList();
        }

        List<?> rawList = (List<?>) rawValue;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map) {
                result.add(new LinkedHashMap<>((Map<String, Object>) item));
            }
        }
        return result;
    }

    private Map<String, Object> safeMap(Object rawValue) {
        if (!(rawValue instanceof Map)) {
            return Collections.emptyMap();
        }
        return new LinkedHashMap<>((Map<String, Object>) rawValue);
    }

    private List<String> toStringList(Object rawValue) {
        if (!(rawValue instanceof List)) {
            return Collections.emptyList();
        }

        List<?> rawList = (List<?>) rawValue;
        List<String> result = new ArrayList<>();
        for (Object item : rawList) {
            String text = stringValue(item);
            if (StringUtils.hasText(text)) {
                result.add(text);
            }
        }
        return result;
    }

    private String extractSnapshotDate(String commonPrefix, String rootPrefix) {
        if (!StringUtils.hasText(commonPrefix) || !commonPrefix.startsWith(rootPrefix)) {
            return null;
        }

        String suffix = commonPrefix.substring(rootPrefix.length());
        if (suffix.endsWith("/")) {
            suffix = suffix.substring(0, suffix.length() - 1);
        }
        return SNAPSHOT_DATE_PATTERN.matcher(suffix).matches() ? suffix : null;
    }

    private String normalizePrefix(String prefix) {
        String value = trimToNull(prefix);
        if (value == null) {
            return "competition/bigdata";
        }
        String normalized = value;
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private int normalizeLookbackDays(int lookbackDays) {
        if (lookbackDays <= 0) {
            return Math.max(defaultLookbackDays, 1);
        }
        return Math.min(lookbackDays, 60);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
