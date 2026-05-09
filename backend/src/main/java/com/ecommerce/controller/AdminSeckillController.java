package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Constants;
import com.ecommerce.common.Log;
import com.ecommerce.common.Result;
import com.ecommerce.dto.SeckillArrangeDTO;
import com.ecommerce.dto.SeckillActivitySaveDTO;
import com.ecommerce.dto.SeckillAuditDTO;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.Category;
import com.ecommerce.entity.Message;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.SeckillActivity;
import com.ecommerce.entity.SeckillActivityApply;
import com.ecommerce.entity.User;
import com.ecommerce.entity.WalletTransaction;
import com.ecommerce.mapper.CategoryMapper;
import com.ecommerce.mapper.CartItemMapper;
import com.ecommerce.mapper.MessageMapper;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.SeckillActivityApplyMapper;
import com.ecommerce.mapper.SeckillActivityMapper;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.mapper.WalletTransactionMapper;
import com.ecommerce.service.ManagementWorkbenchRealtimeService;
import com.ecommerce.service.ModuleSwitchService;
import com.ecommerce.service.RedisStockService;
import com.ecommerce.service.RiskControlService;
import com.ecommerce.service.SeckillService;
import com.ecommerce.utils.JwtUtil;
import com.ecommerce.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/admin/seckill")
public class AdminSeckillController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    @Autowired
    private ModuleSwitchService moduleSwitchService;

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private SeckillActivityApplyMapper seckillActivityApplyMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CartItemMapper cartItemMapper;

    @Autowired
    private WalletTransactionMapper walletTransactionMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private ManagementWorkbenchRealtimeService managementWorkbenchRealtimeService;

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private RedisStockService redisStockService;

    @Autowired
    private RiskControlService riskControlService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${local.server.port:8080}")
    private int serverPort;

    @GetMapping("/diagnostics")
    public Result<?> diagnostics() {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean moduleEnabled = moduleSwitchService.isEnabled("seckill");
        long activityCount = seckillActivityMapper.selectCount(new LambdaQueryWrapper<SeckillActivity>());
        long publishedActivityCount = seckillActivityMapper.selectCount(
                new LambdaQueryWrapper<SeckillActivity>()
                        .eq(SeckillActivity::getPublishStatus, Constants.SeckillPublishStatus.PUBLISHED));
        long applicationCount = seckillActivityApplyMapper.selectCount(new LambdaQueryWrapper<SeckillActivityApply>());
        long approvedApplicationCount = seckillActivityApplyMapper.selectCount(
                new LambdaQueryWrapper<SeckillActivityApply>()
                        .eq(SeckillActivityApply::getAuditStatus, Constants.SeckillAuditStatus.APPROVED));
        List<Map<String, Object>> visibleProducts = moduleEnabled
                ? seckillService.getDisplaySeckillProductCards(200, true)
                : java.util.Collections.emptyList();
        List<Map<String, Object>> visibleGroups = moduleEnabled
                ? seckillService.getDisplaySeckillActivityGroups(200, true)
                : java.util.Collections.emptyList();

        result.put("moduleEnabled", moduleEnabled);
        result.put("activityCount", activityCount);
        result.put("publishedActivityCount", publishedActivityCount);
        result.put("applicationCount", applicationCount);
        result.put("approvedApplicationCount", approvedApplicationCount);
        result.put("visibleProductCount", visibleProducts.size());
        result.put("visibleGroupCount", visibleGroups.size());
        result.put("checkedAt", LocalDateTime.now());
        result.put("diagnosis", buildDiagnosticsText(moduleEnabled, activityCount, publishedActivityCount,
                applicationCount, approvedApplicationCount, visibleProducts.size()));
        return Result.success(result);
    }

    @PostMapping("/stress-test/run")
    @Log(module = "秒杀活动", action = "运行秒杀压测脚本")
    public Result<?> runStressTest(@RequestBody(required = false) Map<String, Object> body) {
        moduleSwitchService.requireEnabled("seckill");

        int requests = clamp(readInt(body, "requests", 200), 1, 500);
        int concurrency = clamp(readInt(body, "concurrency", 20), 1, 100);
        int autoLoginUsers = clamp(readInt(body, "autoLoginUsers", 20), 1, 20);
        int processTimeoutSeconds = clamp(readInt(body, "processTimeoutSeconds", 180), 10, 300);
        boolean cleanupRiskState = readBoolean(body, "cleanupRiskState", false);
        BigDecimal rechargeAmount = readDecimal(body, "rechargeAmount", new BigDecimal("50000"));

        Path projectDir = resolveProjectDir();
        Path managementPcDir = projectDir.resolve("management-pc");
        Path scriptPath = managementPcDir.resolve("scripts").resolve("seckill_concurrency_test.py");
        if (!Files.exists(scriptPath)) {
            throw new BusinessException("秒杀压测脚本不存在: " + scriptPath);
        }

        Path outputDir = projectDir.resolve("output").resolve("seckill-stress");
        Path tokenFile = null;
        List<Long> preparedUserIds = new ArrayList<>();
        try {
            Files.createDirectories(outputDir);
            Path reportPath = outputDir.resolve("seckill-stress-" + System.currentTimeMillis() + ".json");
            tokenFile = outputDir.resolve("seckill-users-" + System.currentTimeMillis() + ".tokens");
            Map<String, Object> seckillData = createStressTestSeckillData(body, autoLoginUsers, requests);
            Long applyId = ((Number) seckillData.get("applyId")).longValue();
            Long productId = ((Number) seckillData.get("productId")).longValue();
            Map<String, Object> preparation = prepareStressTestUsers(autoLoginUsers, rechargeAmount, tokenFile);
            preparedUserIds = extractPreparedUserIds(preparation);
            preparation.put("seckillData", seckillData);

            List<String> command = new ArrayList<>();
            command.add("python");
            command.add(scriptPath.toString());
            command.add("--base-url");
            command.add("http://127.0.0.1:" + serverPort + "/api");
            command.add("--apply-id");
            command.add(String.valueOf(applyId));
            command.add("--product-id");
            command.add(String.valueOf(productId));
            command.add("--token-file");
            command.add(tokenFile.toString());
            command.add("--concurrency");
            command.add(String.valueOf(concurrency));
            command.add("--requests");
            command.add(String.valueOf(requests));
            command.add("--timeout");
            command.add("15");
            command.add("--output");
            command.add(reportPath.toString());

            ProcessBuilder processBuilder = new ProcessBuilder(command)
                    .directory(managementPcDir.toFile())
                    .redirectErrorStream(true);
            processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
            Process process = processBuilder.start();
            boolean finished = process.waitFor(processTimeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException("秒杀压测脚本执行超时，请减少请求数或检查后端服务");
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.exitValue();
            if (!Files.exists(reportPath)) {
                throw new BusinessException("秒杀压测脚本执行失败: " + tail(output));
            }

            Map<String, Object> report = OBJECT_MAPPER.readValue(
                    reportPath.toFile(),
                    new TypeReference<Map<String, Object>>() {}
            );
            report.put("preparation", preparation);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", exitCode == 0);
            result.put("exitCode", exitCode);
            result.put("command", redactCommand(command));
            result.put("applyId", applyId);
            result.put("productId", productId);
            result.put("reportPath", reportPath.toString());
            result.put("stdout", output);
            result.put("report", report);

            return Result.success(exitCode == 0 ? "秒杀测试完成" : "秒杀测试完成，但没有成功订单", result);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("运行秒杀压测脚本失败: " + exception.getMessage());
        } finally {
            if (cleanupRiskState) {
                cleanupStressTestUsers(preparedUserIds);
            }
            if (tokenFile != null) {
                try {
                    Files.deleteIfExists(tokenFile);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @GetMapping("/activities")
    public Result<?> listActivities(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "10") int size,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) Integer publishStatus) {
        moduleSwitchService.requireEnabled("seckill");
        LambdaQueryWrapper<SeckillActivity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SeckillActivity::getName, keyword.trim());
        }
        if (publishStatus != null) {
            wrapper.eq(SeckillActivity::getPublishStatus, publishStatus);
        }
        wrapper.orderByAsc(SeckillActivity::getSortOrder).orderByDesc(SeckillActivity::getId);
        IPage<SeckillActivity> activityPage = seckillActivityMapper.selectPage(new Page<>(page, size), wrapper);
        activityPage.getRecords().forEach(this::fillActivityStats);
        return Result.success(activityPage);
    }

    @PostMapping("/activities")
    @Log(module = "秒杀活动", action = "创建活动")
    public Result<?> createActivity(@Validated @RequestBody SeckillActivitySaveDTO dto) {
        moduleSwitchService.requireEnabled("seckill");
        validateActivityTime(dto.getStartTime(), dto.getEndTime());
        SeckillActivity activity = new SeckillActivity();
        activity.setName(dto.getName().trim());
        activity.setCoverImage(trimToNull(dto.getCoverImage()));
        activity.setDescription(trimToNull(dto.getDescription()));
        activity.setStartTime(dto.getStartTime());
        activity.setEndTime(dto.getEndTime());
        activity.setPublishStatus(dto.getPublishStatus() == null
                ? Constants.SeckillPublishStatus.OFFLINE
                : dto.getPublishStatus());
        activity.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        seckillActivityMapper.insert(activity);
        return Result.success("秒杀活动创建成功", activity);
    }

    @PutMapping("/activities/{id}")
    @Log(module = "秒杀活动", action = "更新活动")
    public Result<?> updateActivity(@PathVariable Long id, @Validated @RequestBody SeckillActivitySaveDTO dto) {
        moduleSwitchService.requireEnabled("seckill");
        SeckillActivity existing = seckillActivityMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("秒杀活动不存在");
        }
        validateActivityTime(dto.getStartTime(), dto.getEndTime());
        if (LocalDateTime.now().isAfter(existing.getStartTime())) {
            boolean changedCoreTime = !Objects.equals(existing.getStartTime(), dto.getStartTime())
                    || !Objects.equals(existing.getEndTime(), dto.getEndTime());
            if (changedCoreTime) {
                throw new BusinessException("活动已开始，不允许修改开始或结束时间");
            }
        }

        SeckillActivity update = new SeckillActivity();
        update.setId(id);
        update.setName(dto.getName().trim());
        update.setCoverImage(trimToNull(dto.getCoverImage()));
        update.setDescription(trimToNull(dto.getDescription()));
        update.setStartTime(dto.getStartTime());
        update.setEndTime(dto.getEndTime());
        update.setPublishStatus(dto.getPublishStatus() == null
                ? existing.getPublishStatus()
                : dto.getPublishStatus());
        update.setSortOrder(dto.getSortOrder() == null ? existing.getSortOrder() : dto.getSortOrder());
        seckillActivityMapper.updateById(update);
        return Result.success("秒杀活动更新成功");
    }

    @PutMapping("/activities/{id}/publish")
    @Log(module = "秒杀活动", action = "切换发布状态")
    public Result<?> publishActivity(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        moduleSwitchService.requireEnabled("seckill");
        SeckillActivity activity = requireActivity(id);
        boolean published = true;
        if (body != null) {
            Object publishedVal = body.get("published");
            Object publishStatusVal = body.get("publishStatus");
            if (publishedVal instanceof Boolean) {
                published = (Boolean) publishedVal;
            } else if (publishStatusVal != null) {
                published = Objects.equals(Integer.valueOf(1), parseIntSafe(publishStatusVal));
            }
        }
        int targetStatus = published
                ? Constants.SeckillPublishStatus.PUBLISHED
                : Constants.SeckillPublishStatus.OFFLINE;
        updateActivityPublishStatus(activity, targetStatus);
        return Result.success(published ? "活动已发布" : "活动已下线");
    }

    @PostMapping("/activities/{id}/offline")
    @Log(module = "秒杀活动", action = "下线活动")
    public Result<?> offlineActivity(@PathVariable Long id) {
        moduleSwitchService.requireEnabled("seckill");
        SeckillActivity activity = requireActivity(id);
        updateActivityPublishStatus(activity, Constants.SeckillPublishStatus.OFFLINE);
        return Result.success("活动已下线");
    }

    @GetMapping({"/applies", "/applications"})
    public Result<?> listApplies(@RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "10") int size,
                                 @RequestParam(required = false) Long activityId,
                                 @RequestParam(required = false) Integer auditStatus,
                                 @RequestParam(required = false) Integer status) {
        moduleSwitchService.requireEnabled("seckill");
        LambdaQueryWrapper<SeckillActivityApply> wrapper = new LambdaQueryWrapper<>();
        if (activityId != null) {
            wrapper.eq(SeckillActivityApply::getActivityId, activityId);
        }
        Integer effectiveStatus = auditStatus != null ? auditStatus : status;
        if (effectiveStatus != null) {
            wrapper.eq(SeckillActivityApply::getAuditStatus, effectiveStatus);
        }
        wrapper.orderByDesc(SeckillActivityApply::getCreateTime);
        IPage<SeckillActivityApply> applyPage = seckillActivityApplyMapper.selectPage(new Page<>(page, size), wrapper);
        applyPage.getRecords().forEach(this::fillApplyRelations);
        return Result.success(applyPage);
    }

    @PostMapping("/applies/{id}/audit")
    @Transactional(rollbackFor = Exception.class)
    @Log(module = "秒杀活动", action = "审核报名")
    public Result<?> auditApply(@PathVariable Long id, @RequestBody SeckillAuditDTO dto) {
        moduleSwitchService.requireEnabled("seckill");
        if (dto == null || dto.getApproved() == null) {
            throw new BusinessException("请指定审核动作");
        }
        return Result.success(doAuditApply(id, dto), null);
    }

    @PostMapping("/applications/{id}/approve")
    @Transactional(rollbackFor = Exception.class)
    @Log(module = "秒杀活动", action = "通过报名")
    public Result<?> approveApply(@PathVariable Long id) {
        moduleSwitchService.requireEnabled("seckill");
        SeckillAuditDTO dto = new SeckillAuditDTO();
        dto.setApproved(true);
        doAuditApply(id, dto);
        return Result.success("审核通过");
    }

    @PostMapping("/applications/{id}/reject")
    @Transactional(rollbackFor = Exception.class)
    @Log(module = "秒杀活动", action = "驳回报名")
    public Result<?> rejectApply(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        moduleSwitchService.requireEnabled("seckill");
        SeckillAuditDTO dto = new SeckillAuditDTO();
        dto.setApproved(false);
        String reason = body == null ? null : Objects.toString(body.get("reason"), null);
        dto.setRejectReason(reason);
        doAuditApply(id, dto);
        return Result.success("已驳回报名");
    }

    @PostMapping({"/applies/{id}/arrange", "/applications/{id}/arrange"})
    @Transactional(rollbackFor = Exception.class)
    @Log(module = "秒杀活动", action = "安排报名场次")
    public Result<?> arrangeApply(@PathVariable Long id, @Validated @RequestBody SeckillArrangeDTO dto) {
        moduleSwitchService.requireEnabled("seckill");
        SeckillActivityApply apply = seckillActivityApplyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException("报名记录不存在");
        }
        if (dto.getActivityId() == null || dto.getActivityId() <= 0) {
            throw new BusinessException("请选择目标活动");
        }
        if (Objects.equals(apply.getActivityId(), dto.getActivityId())) {
            throw new BusinessException("请选择不同的目标场次");
        }
        if (Objects.equals(apply.getAuditStatus(), Constants.SeckillAuditStatus.REVOKED)) {
            throw new BusinessException("商家已撤回报名，无法再安排");
        }
        if (safeInt(apply.getSoldCount()) > 0) {
            throw new BusinessException("报名已产生销量，不能再安排到其他场次");
        }

        LocalDateTime now = LocalDateTime.now();
        SeckillActivity currentActivity = requireActivity(apply.getActivityId());
        if (currentActivity.getStartTime() == null || !currentActivity.getStartTime().isAfter(now)) {
            throw new BusinessException("当前场次已开始，不能再安排");
        }

        SeckillActivity targetActivity = requireActivity(dto.getActivityId());
        if (!Objects.equals(targetActivity.getPublishStatus(), Constants.SeckillPublishStatus.PUBLISHED)) {
            throw new BusinessException("目标活动尚未发布，不能安排");
        }
        if (targetActivity.getStartTime() == null || !targetActivity.getStartTime().isAfter(now)) {
            throw new BusinessException("目标活动必须是未开始的已发布场次");
        }
        if (targetActivity.getEndTime() == null || !targetActivity.getEndTime().isAfter(now)) {
            throw new BusinessException("目标活动已结束，不能安排");
        }

        ensureNoDuplicateApply(targetActivity.getId(), apply.getMerchantId(), apply.getProductId(), id);

        SeckillActivityApply update = new SeckillActivityApply();
        update.setId(id);
        update.setActivityId(targetActivity.getId());
        update.setAuditStatus(Constants.SeckillAuditStatus.APPROVED);
        update.setRejectReason(null);
        update.setAuditTime(now);
        try {
            seckillActivityApplyMapper.updateById(update);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("目标活动下已存在相同商品报名记录，请重新选择场次");
        }
        notifyMerchantArrangeSuccess(apply, currentActivity, targetActivity);

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("scope", "seckill");
        payload.put("applicationId", apply.getId());
        payload.put("activityId", targetActivity.getId());
        payload.put("status", "approved");
        managementWorkbenchRealtimeService.notifyAdmins("seckill-application-updated", payload);
        managementWorkbenchRealtimeService.notifyMerchant(apply.getMerchantId(), "seckill-application-updated", payload);

        return Result.success("报名已安排到新的秒杀场次，并已审核通过");
    }

    private String doAuditApply(Long id, SeckillAuditDTO dto) {
        SeckillActivityApply apply = seckillActivityApplyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException("报名记录不存在");
        }
        if (!Objects.equals(apply.getAuditStatus(), Constants.SeckillAuditStatus.PENDING)) {
            throw new BusinessException("仅待审核状态可操作");
        }
        SeckillActivity activity = requireActivity(apply.getActivityId());
        if (LocalDateTime.now().isAfter(activity.getEndTime())) {
            throw new BusinessException("活动已结束，无法审核");
        }
        SeckillActivityApply update = new SeckillActivityApply();
        update.setId(id);
        update.setAuditTime(LocalDateTime.now());
        if (Boolean.TRUE.equals(dto.getApproved())) {
            update.setAuditStatus(Constants.SeckillAuditStatus.APPROVED);
            update.setRejectReason(null);
        } else {
            if (!StringUtils.hasText(dto.getRejectReason())) {
                throw new BusinessException("驳回时请填写原因");
            }
            update.setAuditStatus(Constants.SeckillAuditStatus.REJECTED);
            update.setRejectReason(dto.getRejectReason().trim());
        }
        seckillActivityApplyMapper.updateById(update);
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("scope", "seckill");
        payload.put("applicationId", apply.getId());
        payload.put("activityId", apply.getActivityId());
        payload.put("status", Boolean.TRUE.equals(dto.getApproved()) ? "approved" : "rejected");
        managementWorkbenchRealtimeService.notifyAdmins("seckill-application-updated", payload);
        managementWorkbenchRealtimeService.notifyMerchant(apply.getMerchantId(), "seckill-application-updated", payload);
        return Boolean.TRUE.equals(dto.getApproved()) ? "审核通过" : "审核驳回";
    }

    private void validateActivityTime(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new BusinessException("活动时间设置不合法");
        }
    }

    private SeckillActivity requireActivity(Long id) {
        SeckillActivity activity = seckillActivityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException("秒杀活动不存在");
        }
        return activity;
    }

    private void fillActivityStats(SeckillActivity activity) {
        if (activity == null || activity.getId() == null) {
            return;
        }
        long applyCount = seckillActivityApplyMapper.selectCount(
                new LambdaQueryWrapper<SeckillActivityApply>().eq(SeckillActivityApply::getActivityId, activity.getId()));
        long approvedCount = seckillActivityApplyMapper.selectCount(
                new LambdaQueryWrapper<SeckillActivityApply>()
                        .eq(SeckillActivityApply::getActivityId, activity.getId())
                        .eq(SeckillActivityApply::getAuditStatus, Constants.SeckillAuditStatus.APPROVED));
        activity.setApplyCount(applyCount);
        activity.setApprovedCount(approvedCount);
    }

    private void fillApplyRelations(SeckillActivityApply apply) {
        if (apply == null) {
            return;
        }
        SeckillActivity activity = seckillActivityMapper.selectById(apply.getActivityId());
        Product product = productMapper.selectById(apply.getProductId());
        User merchant = userMapper.selectById(apply.getMerchantId());
        if (activity != null) {
            apply.setActivityName(activity.getName());
            apply.setActivityStartTime(activity.getStartTime());
            apply.setActivityEndTime(activity.getEndTime());
            apply.setPublishStatus(activity.getPublishStatus());
        }
        if (merchant != null) {
            apply.setMerchantName(StringUtils.hasText(merchant.getNickname())
                    ? merchant.getNickname()
                    : merchant.getUsername());
        }
        if (product != null) {
            apply.setProduct(product);
            apply.setProductName(product.getName());
            apply.setOriginalPrice(apply.getProductPrice() == null ? product.getPrice() : apply.getProductPrice());
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
            throw new BusinessException("目标活动下已存在相同商品报名记录，请重新选择场次");
        }
    }

    private void updateActivityPublishStatus(SeckillActivity activity, int publishStatus) {
        if (activity == null) {
            throw new BusinessException("秒杀活动不存在");
        }
        if (publishStatus == Constants.SeckillPublishStatus.PUBLISHED
                && (activity.getEndTime() == null || !activity.getEndTime().isAfter(LocalDateTime.now()))) {
            throw new BusinessException("活动已结束，无法发布");
        }
        SeckillActivity update = new SeckillActivity();
        update.setId(activity.getId());
        update.setPublishStatus(publishStatus);
        seckillActivityMapper.updateById(update);
    }

    private void notifyMerchantArrangeSuccess(SeckillActivityApply apply,
                                             SeckillActivity currentActivity,
                                             SeckillActivity targetActivity) {
        if (apply == null || apply.getMerchantId() == null || !moduleSwitchService.isEnabled("message")) {
            return;
        }
        Product product = productMapper.selectById(apply.getProductId());
        String productName = product == null ? "该商品" : product.getName();
        String currentName = currentActivity == null ? "原活动" : currentActivity.getName();
        String targetName = targetActivity == null ? "新活动" : targetActivity.getName();
        String targetStartTime = formatDateTime(targetActivity == null ? null : targetActivity.getStartTime());
        String targetEndTime = formatDateTime(targetActivity == null ? null : targetActivity.getEndTime());

        Message message = new Message();
        message.setUserId(apply.getMerchantId());
        message.setTitle("秒杀报名安排通知");
        message.setContent("您提交的商品“" + productName + "”已从“" + currentName + "”调整到“"
                + targetName + "”，并已审核通过。活动时间：" + targetStartTime + " 至 " + targetEndTime + "。");
        message.setType(Constants.MessageType.SYSTEM);
        message.setRelatedId(apply.getId());
        message.setIsRead(0);
        messageMapper.insert(message);
        managementWorkbenchRealtimeService.notifyUserMessageChanged(
                apply.getMerchantId(),
                "user-message-created"
        );
        managementWorkbenchRealtimeService.notifyMerchantMessageChanged(
                apply.getMerchantId(),
                "merchant-message-created",
                java.util.Collections.singletonMap("scope", "message")
        );
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "待定" : value.format(DATE_TIME_FORMATTER);
    }

    private String buildDiagnosticsText(boolean moduleEnabled,
                                        long activityCount,
                                        long publishedActivityCount,
                                        long applicationCount,
                                        long approvedApplicationCount,
                                        long visibleProductCount) {
        if (!moduleEnabled) {
            return "秒杀模块当前关闭，小程序会场会返回空数据。";
        }
        if (activityCount <= 0) {
            return "还没有秒杀活动，请先创建并发布活动。";
        }
        if (publishedActivityCount <= 0) {
            return "已有活动但未发布，小程序只展示已发布活动。";
        }
        if (applicationCount <= 0) {
            return "已有活动但没有商品报名，请商家先提交秒杀报名。";
        }
        if (approvedApplicationCount <= 0) {
            return "已有报名但没有审核通过，小程序只展示审核通过的报名商品。";
        }
        if (visibleProductCount <= 0) {
            return "已有通过报名，但商品可能下架、活动时间不匹配或库存配置异常。";
        }
        return "秒杀会场数据正常，小程序应展示对应活动和商品。";
    }

    private int safeInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private Integer parseIntSafe(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String trimToNull(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }

    private Map<String, Object> createStressTestSeckillData(Map<String, Object> body,
                                                            int userCount,
                                                            int requests) {
        int defaultSeckillStock = Math.max(1, userCount / 2);
        int maxCompetitionStock = userCount > 1 ? userCount - 1 : 1;
        int seckillStock = clamp(readInt(body, "seckillStock", defaultSeckillStock), 1, maxCompetitionStock);
        LocalDateTime now = LocalDateTime.now();
        User merchant = selectStressTestMerchant();
        Category category = ensureStressTestCategory();
        String suffix = now.format(DateTimeFormatter.ofPattern("MMddHHmmss"));

        Product product = new Product();
        product.setName("自动秒杀压测商品-" + suffix);
        product.setDescription("由管理端一键秒杀链路测试自动创建，库存刻意少于测试用户数，用于验证抢购竞争、库存耗尽、限流和黑名单链路。");
        product.setPrice(new BigDecimal("99.00"));
        product.setOriginalPrice(new BigDecimal("129.00"));
        product.setCategoryId(category.getId());
        product.setMerchantId(merchant.getId());
        product.setImage("https://api.dicebear.com/7.x/shapes/svg?seed=seckill-stress-" + suffix);
        product.setImages(List.of(product.getImage()));
        product.setTags(List.of("秒杀压测", "自动创建", "链路测试"));
        product.setStock(seckillStock);
        product.setSalesCount(0);
        product.setRating(new BigDecimal("5.0"));
        product.setStatus(Constants.ProductStatus.ON_SHELF);
        product.setDeleted(0);
        productMapper.insert(product);

        SeckillActivity activity = new SeckillActivity();
        activity.setName("自动秒杀压测场次 " + suffix);
        activity.setCoverImage(product.getImage());
        activity.setDescription("由管理端一键秒杀测试自动创建，测试完成后可在秒杀管理中查看订单与库存结果。");
        activity.setStartTime(now.minusMinutes(1));
        activity.setEndTime(now.plusHours(2));
        activity.setPublishStatus(Constants.SeckillPublishStatus.PUBLISHED);
        activity.setSortOrder(-10000);
        seckillActivityMapper.insert(activity);

        BigDecimal seckillPrice = product.getPrice()
                .multiply(new BigDecimal("0.80"))
                .setScale(2, RoundingMode.HALF_UP);
        if (seckillPrice.compareTo(BigDecimal.ZERO) <= 0 || seckillPrice.compareTo(product.getPrice()) >= 0) {
            seckillPrice = product.getPrice().subtract(new BigDecimal("0.01"));
        }

        SeckillActivityApply apply = new SeckillActivityApply();
        apply.setActivityId(activity.getId());
        apply.setMerchantId(product.getMerchantId());
        apply.setProductId(product.getId());
        apply.setProductPrice(product.getPrice());
        apply.setSeckillPrice(seckillPrice);
        apply.setSeckillStock(seckillStock);
        apply.setSoldCount(0);
        apply.setLimitPerUser(1);
        apply.setAuditStatus(Constants.SeckillAuditStatus.APPROVED);
        apply.setAuditTime(now);
        seckillActivityApplyMapper.insert(apply);
        redisStockService.evictStockCache(product.getId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("created", true);
        data.put("productId", product.getId());
        data.put("productName", product.getName());
        data.put("productStock", product.getStock());
        data.put("productPrice", product.getPrice());
        data.put("categoryId", category.getId());
        data.put("categoryName", category.getName());
        data.put("merchantId", merchant.getId());
        data.put("merchantName", merchant.getNickname());
        data.put("activityId", activity.getId());
        data.put("activityName", activity.getName());
        data.put("applyId", apply.getId());
        data.put("seckillPrice", apply.getSeckillPrice());
        data.put("seckillStock", apply.getSeckillStock());
        data.put("limitPerUser", apply.getLimitPerUser());
        data.put("plannedUsers", userCount);
        data.put("plannedRequests", requests);
        data.put("competitionMode", userCount > seckillStock);
        data.put("competitionNote", "测试用户数大于秒杀库存，用于观察抢购失败、限流和黑名单结果");
        data.put("startTime", activity.getStartTime());
        data.put("endTime", activity.getEndTime());
        return data;
    }

    private User selectStressTestMerchant() {
        User merchant = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, Constants.Role.MERCHANT)
                        .orderByAsc(User::getId)
                        .last("LIMIT 1")
        );
        if (merchant == null) {
            throw new BusinessException("缺少商家账号，无法自动创建秒杀测试商品");
        }
        return merchant;
    }

    private Category ensureStressTestCategory() {
        Category category = categoryMapper.selectOne(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getName, "秒杀压测")
                        .last("LIMIT 1")
        );
        if (category != null) {
            return category;
        }

        Category created = new Category();
        created.setName("秒杀压测");
        created.setParentId(0L);
        created.setIcon("Flash");
        created.setSortOrder(-10000);
        categoryMapper.insert(created);
        return created;
    }

    private Map<String, Object> prepareStressTestUsers(int userCount,
                                                       BigDecimal rechargeAmount,
                                                       Path tokenFile) throws Exception {
        List<User> stressUsers = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, Constants.Role.USER)
                        .eq(User::getStatus, 1)
                        .eq(User::getDeleted, 0)
                        .orderByAsc(User::getId)
                        .last("LIMIT " + userCount)
        );
        if (stressUsers.size() < userCount) {
            throw new BusinessException("可用压测用户不足，期望 " + userCount + " 个，实际仅找到 " + stressUsers.size() + " 个");
        }

        List<String> tokens = new ArrayList<>();
        List<Map<String, Object>> users = new ArrayList<>();
        List<Map<String, Object>> recharges = new ArrayList<>();

        for (User user : stressUsers) {
            String account = StringUtils.hasText(user.getPhone()) ? user.getPhone() : user.getUsername();
            int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
            resetStressUserRiskState(user.getId());
            clearStressUserCart(user.getId());
            tokens.add(jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole(), tokenVersion));

            Map<String, Object> userInfo = new LinkedHashMap<>();
            userInfo.put("account", account);
            userInfo.put("userId", user.getId());
            userInfo.put("nickname", user.getNickname());
            userInfo.put("role", user.getRole());
            users.add(userInfo);

            if (rechargeAmount.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal beforeBalance = user.getBalance() == null ? BigDecimal.ZERO : user.getBalance();
                userMapper.addBalance(user.getId(), rechargeAmount);
                User afterUser = userMapper.selectById(user.getId());
                BigDecimal afterBalance = afterUser == null || afterUser.getBalance() == null
                        ? beforeBalance.add(rechargeAmount)
                        : afterUser.getBalance();

                WalletTransaction tx = new WalletTransaction();
                tx.setUserId(user.getId());
                tx.setType("recharge");
                tx.setAmount(rechargeAmount);
                tx.setBalanceBefore(beforeBalance);
                tx.setBalanceAfter(afterBalance);
                tx.setDescription("[管理员] 秒杀一键压测自动充值");
                walletTransactionMapper.insert(tx);

                Map<String, Object> recharge = new LinkedHashMap<>();
                recharge.put("account", account);
                recharge.put("userId", user.getId());
                recharge.put("amount", rechargeAmount);
                recharge.put("balanceBefore", beforeBalance);
                recharge.put("balanceAfter", afterBalance);
                recharges.add(recharge);
            }
        }

        Files.write(tokenFile, tokens, StandardCharsets.UTF_8);

        Map<String, Object> preparation = new LinkedHashMap<>();
        preparation.put("autoLogin", false);
        preparation.put("tokenSource", "backend-generated-from-seed-users");
        preparation.put("accounts", users);
        preparation.put("rechargeAmount", rechargeAmount);
        preparation.put("recharges", recharges);
        return preparation;
    }

    private void resetStressUserRiskState(Long userId) {
        if (userId == null) {
            return;
        }
        riskControlService.resetRouteSubjectState("seckill_order_create", "USER", String.valueOf(userId));
        redisUtil.delete("rate_limit:seckill:order:create:user:" + userId);
    }

    private void clearStressUserCart(Long userId) {
        if (userId == null) {
            return;
        }
        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId));
    }

    private List<Long> extractPreparedUserIds(Map<String, Object> preparation) {
        List<Long> userIds = new ArrayList<>();
        if (preparation == null) {
            return userIds;
        }
        Object accountsObj = preparation.get("accounts");
        if (!(accountsObj instanceof List<?>)) {
            return userIds;
        }
        for (Object item : (List<?>) accountsObj) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            Object userIdObj = ((Map<?, ?>) item).get("userId");
            if (userIdObj instanceof Number) {
                userIds.add(((Number) userIdObj).longValue());
                continue;
            }
            if (userIdObj != null) {
                try {
                    userIds.add(Long.parseLong(String.valueOf(userIdObj)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return userIds;
    }

    private void cleanupStressTestUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (Long userId : userIds) {
            resetStressUserRiskState(userId);
        }
    }

    private Path resolveProjectDir() {
        Path workingDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path fileName = workingDir.getFileName();
        if (fileName != null && "backend".equalsIgnoreCase(fileName.toString())) {
            return workingDir.getParent();
        }
        return workingDir;
    }

    private int readInt(Map<String, Object> body, String key, int defaultValue) {
        if (body == null || body.get(key) == null) {
            return defaultValue;
        }
        Object value = body.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private boolean readBoolean(Map<String, Object> body, String key, boolean defaultValue) {
        if (body == null || body.get(key) == null) {
            return defaultValue;
        }
        Object value = body.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private BigDecimal readDecimal(Map<String, Object> body, String key, BigDecimal defaultValue) {
        if (body == null || body.get(key) == null) {
            return defaultValue;
        }
        try {
            return new BigDecimal(String.valueOf(body.get(key)));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private List<String> redactCommand(List<String> command) {
        List<String> safeCommand = new ArrayList<>(command);
        for (int i = 0; i < safeCommand.size() - 1; i++) {
            if ("--admin-token".equals(safeCommand.get(i))) {
                safeCommand.set(i + 1, "***");
            }
        }
        return safeCommand;
    }

    private String tail(String text) {
        if (text == null || text.length() <= 1200) {
            return text;
        }
        return text.substring(text.length() - 1200);
    }
}
