package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Constants;
import com.ecommerce.common.Log;
import com.ecommerce.common.Result;
import com.ecommerce.dto.SeckillApplySaveDTO;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.SeckillActivity;
import com.ecommerce.entity.SeckillActivityApply;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.SeckillActivityApplyMapper;
import com.ecommerce.mapper.SeckillActivityMapper;
import com.ecommerce.service.ManagementWorkbenchRealtimeService;
import com.ecommerce.service.ModuleSwitchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Objects;

@RestController
@RequestMapping("/api/merchant/seckill")
public class MerchantSeckillController {

    @Autowired
    private ModuleSwitchService moduleSwitchService;

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private SeckillActivityApplyMapper seckillActivityApplyMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ManagementWorkbenchRealtimeService managementWorkbenchRealtimeService;

    @GetMapping("/activities/available")
    public Result<?> availableActivities(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Integer status) {
        moduleSwitchService.requireEnabled("seckill");
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<SeckillActivity> wrapper = new LambdaQueryWrapper<SeckillActivity>()
                .eq(SeckillActivity::getPublishStatus, Constants.SeckillPublishStatus.PUBLISHED);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SeckillActivity::getName, keyword.trim());
        }
        if (status != null) {
            if (status == Constants.SeckillRuntimeStatus.UPCOMING) {
                wrapper.gt(SeckillActivity::getStartTime, now);
            } else if (status == Constants.SeckillRuntimeStatus.ACTIVE) {
                wrapper.le(SeckillActivity::getStartTime, now).gt(SeckillActivity::getEndTime, now);
            } else if (status == Constants.SeckillRuntimeStatus.ENDED) {
                wrapper.le(SeckillActivity::getEndTime, now);
            }
        } else {
            wrapper.gt(SeckillActivity::getStartTime, now);
        }
        wrapper.orderByAsc(SeckillActivity::getSortOrder).orderByAsc(SeckillActivity::getStartTime);
        return Result.success(seckillActivityMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @GetMapping({"/applies", "/applications"})
    public Result<?> myApplies(@RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "10") int size,
                               @RequestParam(required = false) Integer auditStatus,
                               @RequestParam(required = false) Integer status,
                               HttpServletRequest request) {
        moduleSwitchService.requireEnabled("seckill");
        Long merchantId = (Long) request.getAttribute("userId");
        LambdaQueryWrapper<SeckillActivityApply> wrapper = new LambdaQueryWrapper<SeckillActivityApply>()
                .eq(SeckillActivityApply::getMerchantId, merchantId)
                .orderByDesc(SeckillActivityApply::getCreateTime);
        Integer effectiveStatus = auditStatus != null ? auditStatus : status;
        if (effectiveStatus != null) {
            wrapper.eq(SeckillActivityApply::getAuditStatus, effectiveStatus);
        }
        IPage<SeckillActivityApply> applyPage = seckillActivityApplyMapper.selectPage(new Page<>(page, size), wrapper);
        applyPage.getRecords().forEach(this::fillApplyRelations);
        return Result.success(applyPage);
    }

    @PostMapping({"/applies", "/applications"})
    @Transactional(rollbackFor = Exception.class)
    @Log(module = "商家秒杀", action = "报名秒杀活动")
    public Result<?> createApply(@Validated @RequestBody SeckillApplySaveDTO dto, HttpServletRequest request) {
        moduleSwitchService.requireEnabled("seckill");
        Long merchantId = (Long) request.getAttribute("userId");
        SeckillActivity activity = requireActivityForApply(dto.getActivityId());
        Product product = requireMerchantProduct(dto.getProductId(), merchantId);
        validatePriceAndStock(dto, product);
        ensureNoDuplicateApply(activity.getId(), merchantId, product.getId(), null);

        SeckillActivityApply apply = new SeckillActivityApply();
        apply.setActivityId(activity.getId());
        apply.setMerchantId(merchantId);
        apply.setProductId(product.getId());
        apply.setProductPrice(product.getPrice());
        apply.setSeckillPrice(dto.getSeckillPrice());
        apply.setSeckillStock(dto.getSeckillStock());
        apply.setSoldCount(0);
        apply.setLimitPerUser(dto.getLimitPerUser());
        apply.setAuditStatus(Constants.SeckillAuditStatus.PENDING);
        apply.setRejectReason(null);
        apply.setAuditTime(null);
        try {
            seckillActivityApplyMapper.insert(apply);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("该商品在当前活动下已存在报名记录，请更换活动时段");
        }
        fillApplyRelations(apply);

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("scope", "seckill");
        payload.put("applicationId", apply.getId());
        payload.put("activityId", apply.getActivityId());
        payload.put("status", "pending");
        managementWorkbenchRealtimeService.notifyAdmins("seckill-application-updated", payload);
        managementWorkbenchRealtimeService.notifyMerchant(merchantId, "seckill-application-updated", payload);

        return Result.success("报名提交成功，等待平台审核", apply);
    }

    @PutMapping({"/applies/{id}", "/applications/{id}"})
    @Transactional(rollbackFor = Exception.class)
    @Log(module = "商家秒杀", action = "编辑秒杀报名")
    public Result<?> updateApply(@PathVariable Long id,
                                 @Validated @RequestBody SeckillApplySaveDTO dto,
                                 HttpServletRequest request) {
        moduleSwitchService.requireEnabled("seckill");
        Long merchantId = (Long) request.getAttribute("userId");
        SeckillActivityApply existing = requireOwnedApply(id, merchantId);
        if (!Objects.equals(existing.getAuditStatus(), Constants.SeckillAuditStatus.PENDING)) {
            throw new BusinessException("仅待审核报名可编辑");
        }
        SeckillActivity existingActivity = seckillActivityMapper.selectById(existing.getActivityId());
        if (existingActivity != null && !existingActivity.getStartTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException("活动已开始，不能再修改报名配置");
        }

        SeckillActivity activity = requireActivityForApply(dto.getActivityId());
        Product product = requireMerchantProduct(dto.getProductId(), merchantId);
        validatePriceAndStock(dto, product);
        ensureNoDuplicateApply(activity.getId(), merchantId, product.getId(), id);

        SeckillActivityApply update = new SeckillActivityApply();
        update.setId(id);
        update.setActivityId(activity.getId());
        update.setProductId(product.getId());
        update.setProductPrice(product.getPrice());
        update.setSeckillPrice(dto.getSeckillPrice());
        update.setSeckillStock(dto.getSeckillStock());
        update.setLimitPerUser(dto.getLimitPerUser());
        update.setRejectReason(null);
        update.setAuditTime(null);
        update.setAuditStatus(Constants.SeckillAuditStatus.PENDING);
        try {
            seckillActivityApplyMapper.updateById(update);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("该商品在目标活动下已存在报名记录，请更换活动时段");
        }
        return Result.success("报名信息更新成功");
    }

    @PostMapping("/applies/{id}/revoke")
    @Log(module = "商家秒杀", action = "撤回秒杀报名")
    public Result<?> revokeApply(@PathVariable Long id, HttpServletRequest request) {
        moduleSwitchService.requireEnabled("seckill");
        Long merchantId = (Long) request.getAttribute("userId");
        SeckillActivityApply apply = requireOwnedApply(id, merchantId);
        if (!Objects.equals(apply.getAuditStatus(), Constants.SeckillAuditStatus.PENDING)) {
            throw new BusinessException("仅待审核报名可撤回");
        }
        SeckillActivityApply update = new SeckillActivityApply();
        update.setId(id);
        update.setAuditStatus(Constants.SeckillAuditStatus.REVOKED);
        update.setRejectReason("商家主动撤回");
        update.setAuditTime(LocalDateTime.now());
        seckillActivityApplyMapper.updateById(update);

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("scope", "seckill");
        payload.put("applicationId", apply.getId());
        payload.put("activityId", apply.getActivityId());
        payload.put("status", "revoked");
        managementWorkbenchRealtimeService.notifyAdmins("seckill-application-updated", payload);
        managementWorkbenchRealtimeService.notifyMerchant(merchantId, "seckill-application-updated", payload);

        return Result.success("报名已撤回");
    }

    @DeleteMapping("/applications/{id}")
    @Log(module = "商家秒杀", action = "撤回秒杀报名")
    public Result<?> revokeApplyCompat(@PathVariable Long id, HttpServletRequest request) {
        return revokeApply(id, request);
    }

    private SeckillActivity requireActivityForApply(Long activityId) {
        if (activityId == null || activityId <= 0) {
            throw new BusinessException("活动不存在");
        }
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!Objects.equals(activity.getPublishStatus(), Constants.SeckillPublishStatus.PUBLISHED)) {
            throw new BusinessException("活动尚未发布");
        }
        if (!activity.getStartTime().isAfter(now)) {
            throw new BusinessException("活动已开始，暂不支持报名");
        }
        if (!activity.getEndTime().isAfter(now)) {
            throw new BusinessException("活动已结束，无法报名");
        }
        return activity;
    }

    private Product requireMerchantProduct(Long productId, Long merchantId) {
        if (productId == null || productId <= 0) {
            throw new BusinessException("商品不存在");
        }
        Product product = productMapper.selectById(productId);
        if (product == null || !Objects.equals(product.getMerchantId(), merchantId)) {
            throw new BusinessException("只能报名自己的商品");
        }
        if (!Objects.equals(product.getStatus(), Constants.ProductStatus.ON_SHELF)) {
            throw new BusinessException("仅上架商品可报名秒杀");
        }
        return product;
    }

    private void validatePriceAndStock(SeckillApplySaveDTO dto, Product product) {
        if (dto.getSeckillPrice() == null || dto.getSeckillPrice().doubleValue() <= 0) {
            throw new BusinessException("秒杀价必须大于0");
        }
        if (dto.getSeckillPrice().compareTo(product.getPrice()) >= 0) {
            throw new BusinessException("秒杀价必须小于商品原价");
        }
        if (dto.getSeckillStock() == null || dto.getSeckillStock() <= 0) {
            throw new BusinessException("秒杀库存必须大于0");
        }
        if (product.getStock() == null || dto.getSeckillStock() > product.getStock()) {
            throw new BusinessException("秒杀库存不能超过商品当前库存");
        }
        if (dto.getLimitPerUser() == null || dto.getLimitPerUser() <= 0) {
            throw new BusinessException("每人限购必须大于0");
        }
    }

    private void ensureNoDuplicateApply(Long activityId, Long merchantId, Long productId, Long excludeId) {
        LambdaQueryWrapper<SeckillActivityApply> wrapper = new LambdaQueryWrapper<SeckillActivityApply>()
                .eq(SeckillActivityApply::getActivityId, activityId)
                .eq(SeckillActivityApply::getMerchantId, merchantId)
                .eq(SeckillActivityApply::getProductId, productId);
        if (excludeId != null) {
            wrapper.ne(SeckillActivityApply::getId, excludeId);
        }
        long existed = seckillActivityApplyMapper.selectCount(wrapper);
        if (existed > 0) {
            throw new BusinessException("该商品在目标活动下已存在报名记录，请更换活动时段");
        }
    }

    private SeckillActivityApply requireOwnedApply(Long id, Long merchantId) {
        SeckillActivityApply apply = seckillActivityApplyMapper.selectById(id);
        if (apply == null || !Objects.equals(apply.getMerchantId(), merchantId)) {
            throw new BusinessException("报名记录不存在或无权操作");
        }
        return apply;
    }

    private void fillApplyRelations(SeckillActivityApply apply) {
        if (apply == null) {
            return;
        }
        SeckillActivity activity = seckillActivityMapper.selectById(apply.getActivityId());
        Product product = productMapper.selectById(apply.getProductId());
        if (activity != null) {
            apply.setActivity(activity);
            apply.setActivityName(activity.getName());
            apply.setActivityStartTime(activity.getStartTime());
            apply.setActivityEndTime(activity.getEndTime());
            apply.setPublishStatus(activity.getPublishStatus());
        }
        if (product != null) {
            apply.setProduct(product);
            apply.setProductName(product.getName());
            apply.setOriginalPrice(apply.getProductPrice() == null ? product.getPrice() : apply.getProductPrice());
        }
    }
}
