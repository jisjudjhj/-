package com.ecommerce.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.ecommerce.monitoring.SlowSqlMetricsInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public Interceptor slowSqlMetricsInterceptor(MeterRegistry meterRegistry,
                                                 @Value("${performance.sql.slow-threshold-ms:200}") long slowThresholdMs) {
        return new SlowSqlMetricsInterceptor(meterRegistry, slowThresholdMs);
    }
}
