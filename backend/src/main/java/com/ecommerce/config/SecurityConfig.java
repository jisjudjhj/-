package com.ecommerce.config;

import com.ecommerce.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors().and()
            .csrf().disable()

            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()

            .headers()
                .frameOptions().sameOrigin()
                .xssProtection().and()
                .contentTypeOptions().and()
            .and()

            .authorizeRequests()
                // ── 公开端点 ──
                .antMatchers("/api/auth/login", "/api/auth/register",
                             "/api/auth/send-code", "/api/auth/reset-password",
                             "/api/captcha", "/api/game/auth/verify-token").permitAll()
                .antMatchers(HttpMethod.GET,
                             "/api/products/list", "/api/products/detail/**",
                             "/api/products/categories", "/api/products/banners",
                             "/api/recommendations/hot", "/api/recommendations/algorithm", "/api/recommendations/similar/**",
                             "/api/recommendations/realtime-hot", "/api/recommendations/realtime-hot/overview",
                             "/api/seckill/products",
                             "/api/reviews/product/**", "/api/ai/review-summary/**",
                             "/api/coupons",
                             "/api/search/hot", "/api/search/suggest", "/api/search/query").permitAll()
                .antMatchers(HttpMethod.POST, "/api/ai/product-qa").permitAll()
                .antMatchers("/uploads/**").permitAll()
                .antMatchers("/ws/**").permitAll()
                .antMatchers("/swagger-ui/**", "/swagger-ui.html",
                             "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()

                // ── 角色端点 ──
                .antMatchers("/api/admin/**").hasRole("ADMIN")
                .antMatchers("/api/ai/admin/**").hasRole("ADMIN")
                .antMatchers("/api/merchant/**").hasRole("MERCHANT")

                // ── 其余需认证 ──
                .anyRequest().authenticated()
            .and()

            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

            .exceptionHandling()
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setContentType("application/json;charset=UTF-8");
                    res.setStatus(401);
                    res.getWriter().write("{\"code\":401,\"message\":\"未登录或Token已过期\"}");
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setContentType("application/json;charset=UTF-8");
                    res.setStatus(403);
                    res.getWriter().write("{\"code\":403,\"message\":\"无权访问\"}");
                })
            .and()

            .formLogin().disable()
            .httpBasic().disable();

        return http.build();
    }
}
