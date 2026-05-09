package com.ecommerce.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class ShoppingIntentDTO {

    private String rawMessage;

    private String contextMessage;

    private Long categoryId;

    private String categoryName;

    private BigDecimal budgetMin;

    private BigDecimal budgetMax;

    private List<String> keywords = new ArrayList<>();

    private List<String> preferredBrands = new ArrayList<>();

    private List<String> scenes = new ArrayList<>();

    private String messageType;

    private boolean shoppingRelated;

    private boolean recommendationMode;

    private boolean needClarification;

    private boolean preferHighSales;

    private boolean preferMajorBrand;

    private boolean preferAlternatives;

    private boolean preferLongTermUse;

    private String clarificationQuestion;

    private String analysisSource;

    private String segmentCode;

    private String segmentName;

    private String personaSummary;

    private String strategyHint;

    private List<String> topCategories = new ArrayList<>();

    private List<String> topTags = new ArrayList<>();
}
