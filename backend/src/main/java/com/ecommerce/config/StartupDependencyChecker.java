package com.ecommerce.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

@Component
public class StartupDependencyChecker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupDependencyChecker.class);

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${analytics.kmeans.runner.script-path:}")
    private String runnerScriptPath;

    @Value("${analytics.competition.runner.script-path:}")
    private String competitionRunnerScriptPath;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[StartupCheck] starting dependency readiness check");
        Map<String, Object> snapshot = getSnapshot();
        logDependency("MySQL", getSection(snapshot, "mysql"));
        logDependency("Redis", getSection(snapshot, "redis"));
        logDependency("Python analytics", getSection(snapshot, "pythonAnalytics"));
        logDependency("Competition pipeline", getSection(snapshot, "competitionPipeline"));
        logDependency("Database SQL files", getSection(snapshot, "sqlInit"));
    }

    public Map<String, Object> getSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("mysql", buildMysqlStatus());
        snapshot.put("redis", buildRedisStatus());
        snapshot.put("pythonAnalytics", buildAnalyticsRunnerStatus());
        snapshot.put("competitionPipeline", buildCompetitionRunnerStatus());
        snapshot.put("sqlInit", buildSqlStatus());
        snapshot.put("timestamp", LocalDateTime.now().toString());
        return snapshot;
    }

    private void logDependency(String label, Map<String, Object> item) {
        String status = String.valueOf(item.getOrDefault("status", "UNKNOWN"));
        String target = String.valueOf(item.getOrDefault("target", "--"));
        String message = String.valueOf(item.getOrDefault("message", "--"));
        if ("UP".equalsIgnoreCase(status)) {
            log.info("[StartupCheck] {} ready: {} ({})", label, target, message);
            return;
        }

        log.warn("[StartupCheck] {} status={} target={} ({})", label, status, target, message);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getSection(Map<String, Object> snapshot, String key) {
        Object value = snapshot.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> buildMysqlStatus() {
        Map<String, Object> item = baseStatus(sanitizeDatasourceUrl(datasourceUrl));
        if (dataSource == null) {
            item.put("status", "DOWN");
            item.put("message", "未找到 DataSource Bean");
            return item;
        }

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1")) {
            statement.execute();
            item.put("status", "UP");
            item.put("message", "数据库连接正常");
        } catch (Exception ex) {
            item.put("status", "DOWN");
            item.put("message", safeMessage(ex));
        }
        return item;
    }

    private Map<String, Object> buildRedisStatus() {
        Map<String, Object> item = baseStatus(redisHost + ":" + redisPort);
        if (stringRedisTemplate == null || stringRedisTemplate.getConnectionFactory() == null) {
            item.put("status", "DOWN");
            item.put("message", "未找到 RedisConnectionFactory");
            return item;
        }

        RedisConnection connection = null;
        try {
            connection = stringRedisTemplate.getConnectionFactory().getConnection();
            String pong = connection.ping();
            item.put("status", "UP");
            item.put("message", StringUtils.hasText(pong) ? "PING=" + pong : "缓存连接正常");
        } catch (Exception ex) {
            item.put("status", "DOWN");
            item.put("message", safeMessage(ex));
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
        return item;
    }

    private Map<String, Object> buildAnalyticsRunnerStatus() {
        Path scriptPath = resolveAnalyticsRunnerPath();
        Map<String, Object> item = baseStatus(scriptPath == null ? "<unresolved>" : scriptPath.toString());
        if (scriptPath != null && Files.exists(scriptPath)) {
            item.put("status", "UP");
            item.put("message", "Python 分析脚本已就绪");
            return item;
        }

        if (scriptPath == null) {
            item.put("status", "MISSING");
            item.put("message", "未解析到可用脚本路径");
            return item;
        }

        item.put("status", "MISSING");
        item.put("message", "未找到 Python 分析脚本");
        return item;
    }

    private Map<String, Object> buildCompetitionRunnerStatus() {
        Path scriptPath = resolveCompetitionRunnerPath();
        Map<String, Object> item = baseStatus(scriptPath == null ? "<unresolved>" : scriptPath.toString());
        if (scriptPath != null && Files.exists(scriptPath)) {
            item.put("status", "UP");
            item.put("message", "Competition pipeline 脚本已就绪");
            return item;
        }

        if (scriptPath == null) {
            item.put("status", "MISSING");
            item.put("message", "未配置 run-competition-bigdata.ps1 路径");
            return item;
        }

        item.put("status", "MISSING");
        item.put("message", "缺少 run-competition-bigdata.ps1 文件");
        return item;
    }

    private Map<String, Object> buildSqlStatus() {
        Path schemaSql = resolveSqlFile("schema.sql");
        Path seedSql = resolveSqlFile("seed.sql");
        boolean schemaReady = isReadableNonEmptyFile(schemaSql);
        boolean seedReady = isReadableNonEmptyFile(seedSql);

        Map<String, Object> item = baseStatus(schemaSql.normalize() + " | " + seedSql.normalize());
        if (schemaReady && seedReady) {
            item.put("status", "UP");
            item.put("message", "schema.sql 和 seed.sql 已找到");
        } else {
            item.put("status", "MISSING");
            List<String> missingFiles = new ArrayList<>();
            if (!schemaReady) {
                missingFiles.add(describeSqlFileStatus("schema.sql", schemaSql));
            }
            if (!seedReady) {
                missingFiles.add(describeSqlFileStatus("seed.sql", seedSql));
            }
            item.put("message", "SQL 初始化文件未就绪：" + String.join("、", missingFiles));
        }
        return item;
    }

    private boolean isReadableNonEmptyFile(Path path) {
        try {
            return path != null && Files.isRegularFile(path) && Files.size(path) > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private String describeSqlFileStatus(String fileName, Path path) {
        if (path == null || !Files.exists(path)) {
            return fileName;
        }
        try {
            if (Files.size(path) == 0) {
                return fileName + "(空文件)";
            }
        } catch (Exception ex) {
            return fileName + "(无法读取)";
        }
        return fileName;
    }

    private Path resolveSqlFile(String fileName) {
        Path sqlFile = resolveFromUserDir("src/main/resources/sql/" + fileName);
        if (!Files.exists(sqlFile)) {
            sqlFile = resolveFromUserDir("../src/main/resources/sql/" + fileName);
        }
        return sqlFile;
    }

    private Path resolveAnalyticsRunnerPath() {
        List<Path> candidates = new ArrayList<>();
        if (StringUtils.hasText(runnerScriptPath)) {
            candidates.add(resolvePathCandidate(runnerScriptPath));
        }

        candidates.add(resolveFromUserDir("../scripts/run-kmeans-user-clustering.ps1"));
        candidates.add(resolveFromUserDir("scripts/run-kmeans-user-clustering.ps1"));
        candidates.add(resolveFromUserDir("python_analytics/scheduler.py"));
        candidates.add(resolveFromUserDir("../backend/python_analytics/scheduler.py"));

        for (Path candidate : candidates) {
            if (candidate != null && Files.exists(candidate)) {
                return candidate.normalize();
            }
        }
        return candidates.isEmpty() ? null : candidates.get(0).normalize();
    }

    private Path resolveCompetitionRunnerPath() {
        List<Path> candidates = new ArrayList<>();
        if (StringUtils.hasText(competitionRunnerScriptPath)) {
            candidates.add(resolvePathCandidate(competitionRunnerScriptPath));
        }
        candidates.add(resolveFromUserDir("../scripts/run-competition-bigdata.ps1"));
        candidates.add(resolveFromUserDir("scripts/run-competition-bigdata.ps1"));

        for (Path candidate : candidates) {
            if (candidate != null && Files.exists(candidate)) {
                return candidate.normalize();
            }
        }
        return candidates.isEmpty() ? null : candidates.get(0).normalize();
    }

    private Path resolvePathCandidate(String rawPath) {
        Path direct = Paths.get(rawPath);
        if (direct.isAbsolute()) {
            return direct.normalize();
        }
        return resolveFromUserDir(rawPath);
    }

    private Path resolveFromUserDir(String relativePath) {
        Path userDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        return userDir.resolve(relativePath).normalize();
    }

    private Map<String, Object> baseStatus(String target) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("status", "UNKNOWN");
        item.put("target", StringUtils.hasText(target) ? target : "--");
        item.put("message", "");
        return item;
    }

    private String sanitizeDatasourceUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "<empty>";
        }
        return url.replaceAll("(?i)(password=)[^&]+", "$1***");
    }

    private String safeMessage(Exception ex) {
        String message = ex == null ? null : ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return ex == null ? "未知异常" : ex.getClass().getSimpleName();
        }
        return message.length() > 180 ? message.substring(0, 180) + "..." : message;
    }
}
