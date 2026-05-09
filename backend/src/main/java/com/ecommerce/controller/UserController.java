package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.Result;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.UserBehavior;
import com.ecommerce.entity.UserFavorite;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.UserBehaviorMapper;
import com.ecommerce.mapper.UserFavoriteMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserFavoriteMapper favoriteMapper;

    @Autowired
    private UserBehaviorMapper behaviorMapper;

    @Autowired
    private ProductMapper productMapper;

    @GetMapping("/favorites")
    public Result<?> getFavorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        IPage<UserFavorite> favPage = favoriteMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .orderByDesc(UserFavorite::getCreateTime));
        favPage.getRecords().forEach(f -> f.setProduct(productMapper.selectById(f.getProductId())));
        return Result.success(favPage);
    }

    @PostMapping("/favorites/{productId}")
    public Result<?> addFavorite(@PathVariable Long productId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        long count = favoriteMapper.selectCount(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getProductId, productId));
        if (count > 0) {
            return Result.error("已收藏该商品");
        }
        UserFavorite fav = new UserFavorite();
        fav.setUserId(userId);
        fav.setProductId(productId);
        favoriteMapper.insert(fav);
        return Result.success("收藏成功");
    }

    @DeleteMapping("/favorites/{productId}")
    public Result<?> removeFavorite(@PathVariable Long productId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        favoriteMapper.delete(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getProductId, productId));
        return Result.success("取消收藏");
    }

    @GetMapping("/favorites/check/{productId}")
    public Result<?> checkFavorite(@PathVariable Long productId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        long count = favoriteMapper.selectCount(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getProductId, productId));
        return Result.success(count > 0);
    }

    @GetMapping("/history")
    public Result<?> getHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        LambdaQueryWrapper<UserBehavior> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBehavior::getUserId, userId)
               .eq(UserBehavior::getBehaviorType, "view")
               .isNotNull(UserBehavior::getProductId)
               .orderByDesc(UserBehavior::getCreateTime);

        IPage<UserBehavior> behaviorPage = behaviorMapper.selectPage(new Page<>(page, size), wrapper);

        List<Long> productIds = behaviorPage.getRecords().stream()
                .map(UserBehavior::getProductId).distinct().collect(Collectors.toList());
        if (!productIds.isEmpty()) {
            List<Product> products = productMapper.selectBatchIds(productIds);
            java.util.Map<Long, Product> productMap = products.stream()
                    .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
            behaviorPage.getRecords().forEach(b -> {
                Product p = productMap.get(b.getProductId());
                if (p != null) {
                    b.setProductName(p.getName());
                    b.setProduct(p);
                } else {
                    b.setProductName("已下架");
                }
            });
        }
        return Result.success(behaviorPage);
    }

    @DeleteMapping("/history")
    public Result<?> clearHistory(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        behaviorMapper.delete(
                new LambdaQueryWrapper<UserBehavior>()
                        .eq(UserBehavior::getUserId, userId)
                        .eq(UserBehavior::getBehaviorType, "view"));
        return Result.success("浏览记录已清空");
    }
}
