package com.ecommerce.utils;

import com.ecommerce.entity.Banner;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BannerImageResolver {

    private static final String LEGACY_BANNER_PREFIX = "https://cyy050722.oss-cn-beijing.aliyuncs.com/seed/banners/";

    private static final Map<String, String> TITLE_BANNER_IMAGE_MAP = new LinkedHashMap<>();

    static {
        TITLE_BANNER_IMAGE_MAP.put("华为Mate 60 Pro 卫星通信旗舰", "https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/huawei-mate-60-pro.webp");
        TITLE_BANNER_IMAGE_MAP.put("iPhone 15 Pro 钛金属设计", "https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/iphone-15-pro-max.webp");
        TITLE_BANNER_IMAGE_MAP.put("春季焕新 服饰鞋包专场", "https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/champion-hoodie.webp");
        TITLE_BANNER_IMAGE_MAP.put("美妆大牌日 满300减50", "https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/anessa-sunscreen.webp");
        TITLE_BANNER_IMAGE_MAP.put("超值坚果礼盒 年货节特惠", "https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/three-squirrels-nuts.webp");
        TITLE_BANNER_IMAGE_MAP.put("MacBook Pro M3 创造力无限", "https://cyy050722.oss-cn-beijing.aliyuncs.com/products/2026/03/25/macbook-pro-14.webp");
    }

    private BannerImageResolver() {
    }

    public static List<Banner> normalize(List<Banner> banners) {
        if (banners == null) {
            return null;
        }
        banners.forEach(BannerImageResolver::normalize);
        return banners;
    }

    public static Banner normalize(Banner banner) {
        if (banner == null) {
            return null;
        }
        banner.setImage(resolve(banner.getTitle(), banner.getImage()));
        return banner;
    }

    public static String resolve(String title, String image) {
        String trimmedTitle = StringUtils.hasText(title) ? title.trim() : "";
        String fallbackImage = TITLE_BANNER_IMAGE_MAP.get(trimmedTitle);
        if (!StringUtils.hasText(image)) {
            return fallbackImage != null ? fallbackImage : image;
        }
        if (image.startsWith(LEGACY_BANNER_PREFIX) && fallbackImage != null) {
            return fallbackImage;
        }
        return image;
    }
}
