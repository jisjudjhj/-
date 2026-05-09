package com.ecommerce.controller;

import com.ecommerce.service.InterestCommerceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/interest-commerce")
public class InterestCommerceController {

    @Autowired
    private InterestCommerceService interestCommerceService;

    @GetMapping("/recommendations")
    public Map<String, Object> recommendations(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return interestCommerceService.buildRecommendation(userId, limit);
    }

    @GetMapping("/audit")
    public Map<String, Object> audit(@RequestParam Long userId) {
        return interestCommerceService.auditRecommendation(userId);
    }

    @GetMapping("/segments")
    public Map<String, Object> segments() {
        return interestCommerceService.buildSegments();
    }
}
