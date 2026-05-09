package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.ecommerce.util.InterestTagTaxonomy;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "product", autoResultMap = true)
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private Long categoryId;

    private Long merchantId;

    private String image;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    private Integer stock;

    private Integer salesCount;

    private BigDecimal rating;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private String categoryName;

    @TableField(exist = false)
    private String merchantName;

    @TableField(exist = false)
    private String recommendationToken;

    @TableField(exist = false)
    private String recommendationScene;

    @TableField(exist = false)
    private String recommendationSegmentCode;

    @TableField(exist = false)
    private String recommendationSegmentName;

    @TableField(exist = false)
    private String recommendationSourceType;

    @TableField(exist = false)
    private String recommendReason;

    @TableField(exist = false)
    private List<String> matchedReasonTags;

    @TableField(exist = false)
    private String reasonSummary;

    @TableField(exist = false)
    private String reasonType;

    @TableField(exist = false)
    private String sourceType;

    @TableField(exist = false)
    private String modelVersion;

    @TableField(exist = false)
    private String dataFreshness;

    @TableField(exist = false)
    private BigDecimal recommendationScore;

    @TableField(exist = false)
    private Map<String, BigDecimal> recommendationScoreBreakdown;

    @TableField(exist = false)
    private Long seckillActivityId;

    @TableField(exist = false)
    private Long seckillApplyId;

    @TableField(exist = false)
    private BigDecimal seckillPrice;

    @TableField(exist = false)
    private LocalDateTime seckillStartTime;

    @TableField(exist = false)
    private LocalDateTime seckillEndTime;

    @TableField(exist = false)
    private Integer seckillStock;

    @TableField(exist = false)
    private Integer seckillLimitPerUser;

    /**
     * 0-即将开始 1-进行中 2-已结束
     */
    @TableField(exist = false)
    private Integer seckillStatus;

    public String getName() {
        return trimToNull(name);
    }

    public void setName(String name) {
        this.name = trimToNull(name);
    }

    public String getDescription() {
        return trimToNull(description);
    }

    public void setDescription(String description) {
        this.description = trimToNull(description);
    }

    public String getImage() {
        if (StringUtils.hasText(image)) {
            return image.trim();
        }
        List<String> safeImages = normalizeImages(images);
        return safeImages.isEmpty() ? null : safeImages.get(0);
    }

    public void setImage(String image) {
        this.image = StringUtils.hasText(image) ? image.trim() : null;
        syncImageFields();
    }

    public List<String> getImages() {
        List<String> safeImages = normalizeImages(images);
        if (!safeImages.isEmpty()) {
            return safeImages;
        }
        if (!StringUtils.hasText(image)) {
            return safeImages;
        }
        List<String> fallbackImages = new ArrayList<>();
        fallbackImages.add(image.trim());
        return fallbackImages;
    }

    public void setImages(List<String> images) {
        this.images = normalizeImages(images);
        syncImageFields();
    }

    public List<String> getTags() {
        return InterestTagTaxonomy.expand(normalizeTags(tags), name, description, price, salesCount);
    }

    public void setTags(List<String> tags) {
        List<String> safeTags = normalizeTags(tags);
        this.tags = safeTags.isEmpty() ? null : safeTags;
    }

    public String getMainImage() {
        return getImage();
    }

    public void setMainImage(String mainImage) {
        setImage(mainImage);
    }

    public String getDetailImages() {
        return String.join(",", getImages());
    }

    public void setDetailImages(String detailImages) {
        if (!StringUtils.hasText(detailImages)) {
            setImages(null);
            return;
        }

        String[] rawValues = detailImages.split(",");
        List<String> parsedImages = new ArrayList<>();
        for (String rawValue : rawValues) {
            if (StringUtils.hasText(rawValue)) {
                parsedImages.add(rawValue.trim());
            }
        }
        setImages(parsedImages);
    }

    private void syncImageFields() {
        List<String> safeImages = normalizeImages(images);
        if (!StringUtils.hasText(image)) {
            image = safeImages.isEmpty() ? null : safeImages.get(0);
        } else {
            image = image.trim();
        }

        if (safeImages.isEmpty()) {
            if (StringUtils.hasText(image)) {
                safeImages = new ArrayList<>();
                safeImages.add(image);
            }
        } else if (StringUtils.hasText(image) && !safeImages.contains(image)) {
            safeImages = new ArrayList<>(safeImages);
            safeImages.add(0, image);
        }

        images = safeImages.isEmpty() ? null : safeImages;
    }

    private List<String> normalizeImages(List<String> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> normalized = new ArrayList<>();
        for (String value : source) {
            String safeValue = trimToNull(value);
            if (safeValue != null) {
                normalized.add(safeValue);
            }
        }
        return normalized;
    }

    private List<String> normalizeTags(List<String> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : source) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String safeValue = value.replace("\r", ",")
                    .replace("\n", ",")
                    .replace("，", ",");
            for (String part : safeValue.split(",")) {
                String tag = trimToNull(part);
                if (tag != null) {
                    normalized.add(tag);
                }
            }
        }
        return new ArrayList<>(normalized);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
