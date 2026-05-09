package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Constants;
import com.ecommerce.common.Result;
import com.ecommerce.entity.Banner;
import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;
import com.ecommerce.mapper.BannerMapper;
import com.ecommerce.mapper.CategoryMapper;
import com.ecommerce.service.ModuleSwitchService;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.SeckillService;
import com.ecommerce.utils.BannerImageResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Product", description = "商品、分类与 Banner 接口")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private BannerMapper bannerMapper;

    @Autowired
    private ModuleSwitchService moduleSwitchService;

    @Autowired
    private SeckillService seckillService;

    @GetMapping("/banners")
    @Operation(summary = "获取首页 Banner", description = "返回前台首页展示的轮播图数据。")
    public Result<?> banners() {
        List<Banner> list = bannerMapper.selectList(
                new LambdaQueryWrapper<Banner>()
                        .eq(Banner::getStatus, 1)
                        .orderByAsc(Banner::getSortOrder));
        BannerImageResolver.normalize(list);
        return Result.success(list);
    }

    @GetMapping("/list")
    @Operation(summary = "分页查询商品", description = "支持分类、关键词、价格区间和排序方式筛选商品。")
    public Result<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            moduleSwitchService.requireEnabled("search");
        }
        IPage<Product> result = productService.getProductPage(page, size, categoryId,
                keyword, minPrice, maxPrice, sortField, sortOrder);
        if (moduleSwitchService.isEnabled("seckill")) {
            seckillService.fillProductSeckillInfo(result.getRecords());
        }
        return Result.success(result);
    }

    @GetMapping("/detail/{id}")
    @Operation(summary = "获取商品详情", description = "根据商品 ID 返回商品详情。")
    public Result<?> detail(@PathVariable Long id) {
        Product product = productService.getProductDetail(id);
        if (product == null) {
            return Result.error("商品不存在");
        }
        if (moduleSwitchService.isEnabled("seckill")) {
            seckillService.fillProductSeckillInfo(product);
        }
        return Result.success(product);
    }

    @GetMapping("/categories")
    @Operation(summary = "获取商品分类树", description = "返回商品分类及其子分类结构。")
    public Result<?> categories() {
        List<Category> all = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSortOrder));
        List<Category> roots = all.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0)
                .collect(Collectors.toList());
        roots.forEach(root -> root.setChildren(
                all.stream().filter(c -> root.getId().equals(c.getParentId()))
                        .collect(Collectors.toList())));
        return Result.success(roots);
    }

    @PostMapping
    public Result<?> create(@RequestBody Product product, HttpServletRequest request) {
        checkMerchantOrAdmin(request);
        Long userId = (Long) request.getAttribute("userId");
        product.setMerchantId(userId);
        product.setStatus(Constants.ProductStatus.ON_SHELF);
        product.setSalesCount(0);
        product.setRating(new BigDecimal("5.0"));
        productService.save(product);
        return Result.success("商品创建成功", product);
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody Product product, HttpServletRequest request) {
        checkMerchantOrAdmin(request);
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        Product existing = productService.getById(id);
        if (existing == null) {
            return Result.error("商品不存在");
        }
        if (!Constants.Role.ADMIN.equals(role) && !java.util.Objects.equals(existing.getMerchantId(), userId)) {
            return Result.error(403, "只能修改自己的商品");
        }
        productService.updateById(buildSafeProductUpdate(id, product));
        return Result.success("商品更新成功");
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, HttpServletRequest request) {
        checkMerchantOrAdmin(request);
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        Product existing = productService.getById(id);
        if (existing == null) {
            return Result.error("商品不存在");
        }
        if (!Constants.Role.ADMIN.equals(role) && !java.util.Objects.equals(existing.getMerchantId(), userId)) {
            return Result.error(403, "只能删除自己的商品");
        }
        productService.removeById(id);
        return Result.success("商品删除成功");
    }

    @PutMapping("/{id}/status")
    public Result<?> updateStatus(@PathVariable Long id, @RequestBody java.util.Map<String, Integer> params,
                                   HttpServletRequest request) {
        checkMerchantOrAdmin(request);
        Long userId = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");
        Integer status = params.get("status");
        if (status == null) {
            throw new BusinessException("请指定商品状态");
        }
        Product existing = productService.getById(id);
        if (existing == null) {
            throw new BusinessException("商品不存在");
        }
        if (!Constants.Role.ADMIN.equals(role) && !java.util.Objects.equals(existing.getMerchantId(), userId)) {
            throw new BusinessException(403, "只能修改自己的商品状态");
        }
        Product product = new Product();
        product.setId(id);
        product.setStatus(status);
        productService.updateById(product);
        return Result.success("商品状态更新成功");
    }

    private void checkMerchantOrAdmin(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (!Constants.Role.MERCHANT.equals(role) && !Constants.Role.ADMIN.equals(role)) {
            throw new BusinessException(403, "需要商家或管理员权限");
        }
    }

    private Product buildSafeProductUpdate(Long id, Product source) {
        Product update = new Product();
        update.setId(id);
        update.setName(source.getName());
        update.setDescription(source.getDescription());
        update.setPrice(source.getPrice());
        update.setOriginalPrice(source.getOriginalPrice());
        update.setCategoryId(source.getCategoryId());
        update.setImage(source.getImage());
        update.setImages(source.getImages());
        update.setTags(source.getTags());
        update.setStock(source.getStock());
        return update;
    }
}
