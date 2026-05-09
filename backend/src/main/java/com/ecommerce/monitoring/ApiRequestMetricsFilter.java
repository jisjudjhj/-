package com.ecommerce.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class ApiRequestMetricsFilter extends OncePerRequestFilter {

    private final Counter totalCounter;
    private final Counter errorCounter;

    public ApiRequestMetricsFilter(MeterRegistry meterRegistry) {
        this.totalCounter = Counter.builder("ecommerce.api.requests.total")
                .description("Total API requests")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("ecommerce.api.errors.total")
                .description("Total API requests with 5xx or unhandled exception")
                .register(meterRegistry);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean isError = false;
        try {
            filterChain.doFilter(request, response);
            if (response.getStatus() >= 500) {
                isError = true;
            }
        } catch (ServletException | IOException | RuntimeException ex) {
            isError = true;
            throw ex;
        } finally {
            totalCounter.increment();
            if (isError) {
                errorCounter.increment();
            }
        }
    }
}
