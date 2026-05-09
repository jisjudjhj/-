package com.ecommerce.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationControllerRouteTest {

    @Test
    void recommendationControllerShouldNotExposeAbTestRoutes() {
        Set<String> routes = new HashSet<>();
        for (Method method : RecommendationController.class.getDeclaredMethods()) {
            GetMapping getMapping = method.getAnnotation(GetMapping.class);
            if (getMapping != null) {
                for (String value : getMapping.value()) {
                    routes.add(value);
                }
            }

            PostMapping postMapping = method.getAnnotation(PostMapping.class);
            if (postMapping != null) {
                for (String value : postMapping.value()) {
                    routes.add(value);
                }
            }
        }

        RequestMapping rootMapping = RecommendationController.class.getAnnotation(RequestMapping.class);
        assertTrue(rootMapping != null && rootMapping.value().length == 1);
        assertTrue("/api/recommendations".equals(rootMapping.value()[0]));
        assertTrue(routes.contains("/events"));
        assertFalse(routes.contains("/abtest-report"));
        assertFalse(routes.contains("/abtest-reset"));
    }
}
