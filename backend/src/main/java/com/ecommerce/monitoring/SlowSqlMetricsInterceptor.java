package com.ecommerce.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, org.apache.ibatis.cache.CacheKey.class, BoundSql.class})
})
public class SlowSqlMetricsInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(SlowSqlMetricsInterceptor.class);
    private static final int SQL_PREVIEW_MAX_LENGTH = 240;

    private final long slowThresholdMs;
    private final Counter slowSqlCounter;

    public SlowSqlMetricsInterceptor(MeterRegistry meterRegistry, long slowThresholdMs) {
        this.slowThresholdMs = Math.max(1L, slowThresholdMs);
        this.slowSqlCounter = Counter.builder("ecommerce.db.slow_query.total")
                .description("Total number of SQL statements whose latency exceeds threshold")
                .register(meterRegistry);
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startNanos = System.nanoTime();
        Throwable throwable = null;
        try {
            return invocation.proceed();
        } catch (Throwable ex) {
            throwable = ex;
            throw ex;
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            if (durationMs >= slowThresholdMs) {
                slowSqlCounter.increment();
                logSlowSql(invocation, durationMs, throwable);
            }
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // no-op
    }

    private void logSlowSql(Invocation invocation, long durationMs, Throwable throwable) {
        Object[] args = invocation.getArgs();
        if (args == null || args.length == 0 || !(args[0] instanceof MappedStatement)) {
            return;
        }
        MappedStatement mappedStatement = (MappedStatement) args[0];
        Object parameterObject = args.length > 1 ? args[1] : null;
        BoundSql boundSql = resolveBoundSql(args, mappedStatement, parameterObject);
        String sql = boundSql == null ? "" : normalizeSql(boundSql.getSql());
        if (sql.length() > SQL_PREVIEW_MAX_LENGTH) {
            sql = sql.substring(0, SQL_PREVIEW_MAX_LENGTH) + "...";
        }

        if (throwable == null) {
            log.warn("[SlowSQL] cost={}ms, threshold={}ms, statementId={}, sql={}",
                    durationMs, slowThresholdMs, mappedStatement.getId(), sql);
        } else {
            log.warn("[SlowSQL] cost={}ms, threshold={}ms, statementId={}, sql={}, error={}",
                    durationMs, slowThresholdMs, mappedStatement.getId(), sql, throwable.getMessage());
        }
    }

    private BoundSql resolveBoundSql(Object[] args, MappedStatement mappedStatement, Object parameterObject) {
        if (args.length >= 6 && args[5] instanceof BoundSql) {
            return (BoundSql) args[5];
        }
        return mappedStatement.getBoundSql(parameterObject);
    }

    private String normalizeSql(String sql) {
        if (sql == null) {
            return "";
        }
        return sql.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
