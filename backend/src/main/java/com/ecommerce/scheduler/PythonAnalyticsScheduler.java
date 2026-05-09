package com.ecommerce.scheduler;

import com.ecommerce.service.impl.KmeansCoverageBackfillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class PythonAnalyticsScheduler {

    private static final Logger log = LoggerFactory.getLogger(PythonAnalyticsScheduler.class);

    private final KmeansCoverageBackfillService backfillService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${analytics.python.enabled:true}")
    private boolean enabled;

    @Value("${analytics.python.shell:powershell}")
    private String runnerShell;

    @Value("${analytics.python.script-path:}")
    private String runnerScriptPath;

    @Value("${analytics.python.timeout-minutes:90}")
    private int timeoutMinutes;

    @Value("${analytics.python.recommendation.jobs:recommendation}")
    private String recommendationJobs;

    @Value("${analytics.python.kmeans.jobs:kmeans,recommendation}")
    private String kmeansJobs;

    @Value("${analytics.python.kmeans.k:3}")
    private int kmeansK;

    @Value("${analytics.python.kmeans.auto-k:true}")
    private boolean kmeansAutoK;

    @Value("${analytics.python.kmeans.min-k:2}")
    private int kmeansMinK;

    @Value("${analytics.python.kmeans.max-k:6}")
    private int kmeansMaxK;

    @Value("${analytics.python.backfill.enabled:true}")
    private boolean backfillEnabled;

    public PythonAnalyticsScheduler(KmeansCoverageBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @Scheduled(cron = "${analytics.python.recommendation.cron:0 8 * * * ?}")
    public void runHourlyRecommendationSnapshot() {
        executePythonCycle("hourly_recommendation", recommendationJobs, false);
    }

    @Scheduled(cron = "${analytics.python.kmeans.cron:0 15 2 * * ?}")
    public void runDailyKmeansAndRecommendation() {
        executePythonCycle("daily_kmeans_recommendation", kmeansJobs, true);
    }

    @Scheduled(cron = "${analytics.python.backfill.cron:0 35 2 * * ?}")
    public void runDailyCoverageBackfill() {
        if (!enabled || !backfillEnabled) {
            return;
        }
        try {
            Map<String, Object> result = backfillService.backfillLatestTaskMissingUsers();
            log.info("[PythonAnalytics] KMeans coverage backfill finished: {}", result);
        } catch (Exception ex) {
            log.warn("[PythonAnalytics] KMeans coverage backfill failed: {}", ex.getMessage(), ex);
        }
    }

    public Map<String, Object> triggerManualCycle(String taskName, String jobs, boolean includeKmeansParams) {
        String safeTaskName = StringUtils.hasText(taskName) ? taskName.trim() : "manual_python_analytics";
        String safeJobs = StringUtils.hasText(jobs) ? jobs.trim() : "analytics,kmeans,recommendation";
        if (!enabled) {
            Map<String, Object> disabled = new java.util.LinkedHashMap<>();
            disabled.put("accepted", false);
            disabled.put("message", "Python 分析任务已关闭");
            return disabled;
        }
        if (running.get()) {
            Map<String, Object> busy = new java.util.LinkedHashMap<>();
            busy.put("accepted", false);
            busy.put("message", "当前已有 Python 分析任务运行中");
            return busy;
        }
        java.util.concurrent.CompletableFuture.runAsync(() ->
                executePythonCycle(safeTaskName, safeJobs, includeKmeansParams));
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("accepted", true);
        payload.put("taskName", safeTaskName);
        payload.put("jobs", safeJobs);
        payload.put("includeKmeansParams", includeKmeansParams);
        payload.put("message", "Python 分析任务已提交");
        return payload;
    }

    public boolean isRunning() {
        return running.get();
    }

    private void executePythonCycle(String taskName, String jobs, boolean includeKmeansParams) {
        if (!enabled) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.info("[PythonAnalytics] Skip {} because another analytics cycle is running.", taskName);
            return;
        }

        try {
            Path scriptPath = resolveScriptPath();
            if (scriptPath == null || !Files.exists(scriptPath)) {
                log.warn("[PythonAnalytics] Skip {} because script is missing: {}", taskName,
                        scriptPath == null ? "null" : scriptPath);
                return;
            }

            List<String> command = buildProcessCommand(scriptPath, jobs, includeKmeansParams);
            log.info("[PythonAnalytics] Start {} -> {}", taskName, previewCommand(command));

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            processBuilder.directory(scriptPath.getParent().toFile());

            Process process = processBuilder.start();
            drainProcessOutput(process, taskName);

            int safeTimeoutMinutes = Math.max(10, Math.min(timeoutMinutes, 360));
            boolean finished = process.waitFor(safeTimeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                log.warn("[PythonAnalytics] {} timeout after {} minutes, process destroyed.",
                        taskName, safeTimeoutMinutes);
                return;
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("[PythonAnalytics] {} finished successfully.", taskName);
            } else {
                log.warn("[PythonAnalytics] {} finished with non-zero exit code: {}", taskName, exitCode);
            }
        } catch (Exception ex) {
            log.warn("[PythonAnalytics] {} failed: {}", taskName, ex.getMessage(), ex);
        } finally {
            running.set(false);
        }
    }

    private List<String> buildProcessCommand(Path scriptPath, String jobs, boolean includeKmeansParams) {
        List<String> args = new ArrayList<>();
        args.add(runnerShell);
        args.add("-ExecutionPolicy");
        args.add("Bypass");
        args.add("-File");
        args.add(scriptPath.toString());
        args.add("-RunOnce");
        args.add("-SnapshotDate");
        args.add(LocalDate.now().toString());
        args.add("-Jobs");
        args.add(StringUtils.hasText(jobs) ? jobs : "kmeans,recommendation");

        if (includeKmeansParams) {
            args.add("-K");
            args.add(String.valueOf(Math.max(2, kmeansK)));
            args.add("-AutoK");
            args.add(String.valueOf(kmeansAutoK).toLowerCase(Locale.ROOT));
            args.add("-MinK");
            args.add(String.valueOf(Math.max(2, kmeansMinK)));
            args.add("-MaxK");
            args.add(String.valueOf(Math.max(Math.max(2, kmeansMinK), kmeansMaxK)));
        }
        return args;
    }

    private Path resolveScriptPath() {
        List<Path> candidates = new ArrayList<>();
        if (StringUtils.hasText(runnerScriptPath)) {
            candidates.add(resolvePathCandidate(runnerScriptPath));
        }

        Path userDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        candidates.add(userDir.resolve("../scripts/run-python-analytics.ps1").normalize());
        candidates.add(userDir.resolve("scripts/run-python-analytics.ps1").normalize());
        if (userDir.getParent() != null) {
            candidates.add(userDir.getParent().resolve("scripts/run-python-analytics.ps1").normalize());
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

    private void drainProcessOutput(Process process, String taskName) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[PythonAnalytics:{}] {}", taskName, line);
            }
        }
    }

    private String previewCommand(List<String> command) {
        StringBuilder builder = new StringBuilder();
        for (String item : command) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            if (item != null && item.contains(" ")) {
                builder.append('"').append(item).append('"');
            } else {
                builder.append(item);
            }
        }
        return builder.toString();
    }
}
