package com.ecommerce.config;

import com.ecommerce.interceptor.RiskControlInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private RiskControlInterceptor riskControlInterceptor;

    @Value("${storage.local.base-path:./uploads}")
    private String uploadPath;

    @Value("${storage.type:local}")
    private String storageType;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(riskControlInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/admin/risk/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!"local".equalsIgnoreCase(storageType)) {
            return;
        }
        String path = uploadPath;
        if (!path.endsWith("/")) {
            path += "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + path);
    }
}
