package com.ecommerce.service;

import com.ecommerce.common.ModuleDisabledException;
import com.ecommerce.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ModuleSwitchService {

    private static final String KEY_PREFIX = "module:switch:";

    @Autowired
    private RedisUtil redisUtil;

    private static final Set<String> CORE_MODULES = new LinkedHashSet<>(
            Arrays.asList("search", "wallet", "message", "register"));

    public static final LinkedHashMap<String, ModuleDef> ALL_MODULES = new LinkedHashMap<>();

    static {
        ALL_MODULES.put("recommendation", new ModuleDef(
                "推荐系统", "个性化推荐、相似商品、猜你喜欢", "recommendation",
                "high", true, Collections.<String>emptyList(), "影响前台首页推荐、猜你喜欢与推荐预览"));
        ALL_MODULES.put("ab-test", new ModuleDef(
                "A/B 实验", "推荐算法 A/B 测试分组与效果追踪", "recommendation",
                "medium", true, Arrays.asList("recommendation"), "影响推荐实验分流与效果报表"));
        ALL_MODULES.put("coupon", new ModuleDef(
                "优惠券", "优惠券领取、使用与管理", "marketing",
                "medium", true, Collections.<String>emptyList(), "影响领券、下单优惠与营销活动"));
        ALL_MODULES.put("seckill", new ModuleDef(
                "限时秒杀", "秒杀活动管理、商家报名与用户抢购", "marketing",
                "high", true, Collections.<String>emptyList(), "影响秒杀会场、活动报名与订单链路"));
        ALL_MODULES.put("review", new ModuleDef(
                "商品评价", "用户评价提交与展示", "product",
                "medium", true, Collections.<String>emptyList(), "影响商品详情评价与口碑反馈"));
        ALL_MODULES.put("wallet", new ModuleDef(
                "钱包支付", "余额充值与钱包支付", "transaction",
                "critical", true, Collections.<String>emptyList(), "影响支付、退款和余额变更"));
        ALL_MODULES.put("refund", new ModuleDef(
                "退款售后", "退款申请与审核处理", "transaction",
                "high", true, Arrays.asList("wallet"), "影响退款申请、审核与回款"));
        ALL_MODULES.put("search", new ModuleDef(
                "搜索服务", "商品搜索、热搜词、搜索建议", "product",
                "high", true, Collections.<String>emptyList(), "影响搜索入口和商品检索"));
        ALL_MODULES.put("message", new ModuleDef(
                "站内消息", "系统通知与消息推送", "system",
                "high", true, Collections.<String>emptyList(), "影响通知触达、站内信和运营广播"));
        ALL_MODULES.put("register", new ModuleDef(
                "用户注册", "新用户注册功能", "user",
                "high", true, Collections.<String>emptyList(), "影响新用户注册和增长入口"));
        ALL_MODULES.put("ai-chat", new ModuleDef(
                "AI 购物助手", "对话式 AI 商品推荐", "ai",
                "medium", true, Collections.<String>emptyList(), "影响用户端 AI 导购对话"));
        ALL_MODULES.put("ai-merchant-copilot", new ModuleDef(
                "AI 商家助手", "商家对话式运营助手与一键生成商品上架文案", "ai",
                "medium", true, Arrays.asList("ai-chat"), "影响商家 AI 运营能力"));
        ALL_MODULES.put("ai-review-summary", new ModuleDef(
                "AI 评价摘要", "AI 自动总结商品评价", "ai",
                "low", true, Arrays.asList("ai-chat", "review"), "影响商品评价摘要与洞察"));
        ALL_MODULES.put("ai-product-qa", new ModuleDef(
                "AI 商品问答", "AI 回答商品相关问题", "ai",
                "low", true, Arrays.asList("ai-chat"), "影响商品问答与客服答疑"));
    }

    public boolean isEnabled(String module) {
        try {
            Object val = redisUtil.get(KEY_PREFIX + module);
            if (val == null) {
                return true;
            }
            return Boolean.parseBoolean(val.toString());
        } catch (Exception e) {
            return true;
        }
    }

    public void setEnabled(String module, boolean enabled) {
        redisUtil.set(KEY_PREFIX + module, String.valueOf(enabled));
    }

    public void requireEnabled(String module) {
        if (!isEnabled(module)) {
            throw new ModuleDisabledException(module, getModuleName(module));
        }
    }

    public String getModuleName(String module) {
        ModuleDef def = ALL_MODULES.get(module);
        return def != null ? def.name : module;
    }

    public Map<String, Object> getAllSwitches() {
        Map<String, Boolean> statusMap = loadStatusMap();
        Map<String, List<String>> reverseDeps = buildReverseDependencies();
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, ModuleDef> entry : ALL_MODULES.entrySet()) {
            String module = entry.getKey();
            ModuleDef def = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", def.name);
            item.put("desc", def.desc);
            item.put("group", def.group);
            item.put("enabled", statusMap.get(module));
            item.put("riskLevel", def.riskLevel);
            item.put("impact", def.impact);
            item.put("defaultEnabled", def.defaultEnabled);
            item.put("isCore", CORE_MODULES.contains(module));

            List<String> dependencies = new ArrayList<>(def.dependencies);
            List<String> requiredBy = new ArrayList<>(reverseDeps.getOrDefault(module, Collections.<String>emptyList()));
            item.put("dependencies", dependencies);
            item.put("requiredBy", requiredBy);

            int disabledDependencyCount = 0;
            for (String dependency : dependencies) {
                if (!Boolean.TRUE.equals(statusMap.get(dependency))) {
                    disabledDependencyCount++;
                }
            }
            int activeDependentCount = 0;
            for (String dependent : requiredBy) {
                if (Boolean.TRUE.equals(statusMap.get(dependent))) {
                    activeDependentCount++;
                }
            }
            item.put("disabledDependencyCount", disabledDependencyCount);
            item.put("activeDependentCount", activeDependentCount);
            item.put("canToggleOffSafely", activeDependentCount == 0);

            result.put(module, item);
        }
        return result;
    }

    public Map<String, Object> getSwitchSummary() {
        Map<String, Boolean> statusMap = loadStatusMap();
        int total = ALL_MODULES.size();
        int enabled = 0;
        int disabled = 0;
        int coreDisabled = 0;
        int criticalDisabled = 0;
        for (Map.Entry<String, ModuleDef> entry : ALL_MODULES.entrySet()) {
            String module = entry.getKey();
            ModuleDef def = entry.getValue();
            boolean moduleEnabled = Boolean.TRUE.equals(statusMap.get(module));
            if (moduleEnabled) {
                enabled++;
            } else {
                disabled++;
                if (CORE_MODULES.contains(module)) {
                    coreDisabled++;
                }
                if ("critical".equals(def.riskLevel)) {
                    criticalDisabled++;
                }
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("enabled", enabled);
        summary.put("disabled", disabled);
        summary.put("coreDisabled", coreDisabled);
        summary.put("criticalDisabled", criticalDisabled);
        summary.put("healthy", coreDisabled == 0 && criticalDisabled == 0);
        return summary;
    }

    public Map<String, Object> applySwitch(String module,
                                           boolean enabled,
                                           boolean force,
                                           boolean autoEnableDependencies) {
        if (!isValidModule(module)) {
            return buildBlockedResult("无效的模块名称: " + module, Collections.singletonList(module));
        }
        Map<String, Boolean> requested = new LinkedHashMap<>();
        requested.put(module, enabled);
        return applyBatch(requested, force, autoEnableDependencies);
    }

    public Map<String, Object> applyBatch(Map<String, Boolean> switches,
                                          boolean force,
                                          boolean autoEnableDependencies) {
        Map<String, Boolean> filteredRequested = new LinkedHashMap<>();
        if (switches != null) {
            for (Map.Entry<String, Boolean> entry : switches.entrySet()) {
                String module = entry.getKey();
                if (!isValidModule(module) || entry.getValue() == null) {
                    continue;
                }
                filteredRequested.put(module, entry.getValue());
            }
        }
        if (filteredRequested.isEmpty()) {
            return buildAppliedResult(Collections.<String, Boolean>emptyMap(),
                    Collections.<String>emptyList(),
                    Collections.<String>emptyList(),
                    Collections.<Map<String, Object>>emptyList(),
                    "没有可执行的模块变更");
        }

        Map<String, Boolean> currentStatus = loadStatusMap();
        Map<String, Boolean> plannedStatus = new LinkedHashMap<>(currentStatus);
        Map<String, List<String>> reverseDeps = buildReverseDependencies();

        List<String> autoEnabledModules = new ArrayList<>();
        List<String> cascadedDisabledModules = new ArrayList<>();
        List<Map<String, Object>> conflicts = new ArrayList<>();
        List<Map<String, Object>> warnings = new ArrayList<>();

        for (Map.Entry<String, Boolean> entry : filteredRequested.entrySet()) {
            String module = entry.getKey();
            boolean targetEnabled = Boolean.TRUE.equals(entry.getValue());
            if (targetEnabled) {
                resolveEnableDependencies(module, plannedStatus, autoEnabledModules, conflicts, force, autoEnableDependencies);
                plannedStatus.put(module, true);
                continue;
            }

            Set<String> activeDependents = collectActiveDependents(module, plannedStatus, reverseDeps);
            activeDependents.removeAll(collectDisabledInRequest(filteredRequested));
            if (!activeDependents.isEmpty()) {
                if (!force) {
                    conflicts.add(buildConflict("DEPENDENT_ACTIVE", module, new ArrayList<>(activeDependents),
                            "存在依赖该模块且仍在启用的子模块"));
                    continue;
                }
                for (String dependent : activeDependents) {
                    if (Boolean.TRUE.equals(plannedStatus.get(dependent))) {
                        plannedStatus.put(dependent, false);
                        if (!cascadedDisabledModules.contains(dependent)) {
                            cascadedDisabledModules.add(dependent);
                        }
                    }
                }
            }
            plannedStatus.put(module, false);
        }

        if (!conflicts.isEmpty()) {
            return buildBlockedResult("存在依赖冲突，请先处理后再提交", conflicts);
        }

        Map<String, Boolean> changed = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> entry : plannedStatus.entrySet()) {
            String module = entry.getKey();
            boolean before = Boolean.TRUE.equals(currentStatus.get(module));
            boolean after = Boolean.TRUE.equals(entry.getValue());
            if (before != after) {
                setEnabled(module, after);
                changed.put(module, after);
            }
        }

        if (!autoEnabledModules.isEmpty()) {
            warnings.add(buildConflict("AUTO_ENABLE_DEPENDENCY", "system", autoEnabledModules,
                    "已自动开启依赖模块，保证链路可用"));
        }
        if (!cascadedDisabledModules.isEmpty()) {
            warnings.add(buildConflict("CASCADE_DISABLE_DEPENDENT", "system", cascadedDisabledModules,
                    "已联动关闭依赖模块，避免出现不可用状态"));
        }

        String message = changed.isEmpty() ? "模块状态未发生变化" : "模块状态更新成功";
        return buildAppliedResult(changed, autoEnabledModules, cascadedDisabledModules, warnings, message);
    }

    public boolean isValidModule(String module) {
        return ALL_MODULES.containsKey(module);
    }

    public static class ModuleDef {
        public final String name;
        public final String desc;
        public final String group;
        public final String riskLevel;
        public final boolean defaultEnabled;
        public final List<String> dependencies;
        public final String impact;

        public ModuleDef(String name,
                         String desc,
                         String group,
                         String riskLevel,
                         boolean defaultEnabled,
                         List<String> dependencies,
                         String impact) {
            this.name = name;
            this.desc = desc;
            this.group = group;
            this.riskLevel = riskLevel;
            this.defaultEnabled = defaultEnabled;
            this.dependencies = dependencies == null ? Collections.<String>emptyList() : new ArrayList<>(dependencies);
            this.impact = impact;
        }
    }

    private Map<String, Boolean> loadStatusMap() {
        Map<String, Boolean> statusMap = new LinkedHashMap<>();
        for (String module : ALL_MODULES.keySet()) {
            statusMap.put(module, isEnabled(module));
        }
        return statusMap;
    }

    private Map<String, List<String>> buildReverseDependencies() {
        Map<String, List<String>> reverse = new HashMap<>();
        for (Map.Entry<String, ModuleDef> entry : ALL_MODULES.entrySet()) {
            String module = entry.getKey();
            for (String dependency : entry.getValue().dependencies) {
                if (!isValidModule(dependency)) {
                    continue;
                }
                List<String> dependents = reverse.get(dependency);
                if (dependents == null) {
                    dependents = new ArrayList<>();
                    reverse.put(dependency, dependents);
                }
                if (!dependents.contains(module)) {
                    dependents.add(module);
                }
            }
        }
        return reverse;
    }

    private Set<String> collectDisabledInRequest(Map<String, Boolean> requested) {
        Set<String> disabled = new LinkedHashSet<>();
        if (requested == null) {
            return disabled;
        }
        for (Map.Entry<String, Boolean> entry : requested.entrySet()) {
            if (Boolean.FALSE.equals(entry.getValue())) {
                disabled.add(entry.getKey());
            }
        }
        return disabled;
    }

    private void resolveEnableDependencies(String module,
                                           Map<String, Boolean> plannedStatus,
                                           List<String> autoEnabledModules,
                                           List<Map<String, Object>> conflicts,
                                           boolean force,
                                           boolean autoEnableDependencies) {
        ModuleDef def = ALL_MODULES.get(module);
        if (def == null || def.dependencies.isEmpty()) {
            return;
        }
        for (String dependency : def.dependencies) {
            if (!isValidModule(dependency)) {
                continue;
            }
            if (Boolean.TRUE.equals(plannedStatus.get(dependency))) {
                continue;
            }
            if (!(force || autoEnableDependencies)) {
                conflicts.add(buildConflict("DEPENDENCY_DISABLED", module,
                        Collections.singletonList(dependency), "依赖模块未开启"));
                continue;
            }
            plannedStatus.put(dependency, true);
            if (!autoEnabledModules.contains(dependency)) {
                autoEnabledModules.add(dependency);
            }
            resolveEnableDependencies(dependency, plannedStatus, autoEnabledModules, conflicts, force, autoEnableDependencies);
        }
    }

    private Set<String> collectActiveDependents(String module,
                                                Map<String, Boolean> plannedStatus,
                                                Map<String, List<String>> reverseDeps) {
        Set<String> result = new LinkedHashSet<>();
        List<String> direct = reverseDeps.get(module);
        if (direct == null || direct.isEmpty()) {
            return result;
        }
        for (String dependent : direct) {
            if (!Boolean.TRUE.equals(plannedStatus.get(dependent))) {
                continue;
            }
            result.add(dependent);
            result.addAll(collectActiveDependents(dependent, plannedStatus, reverseDeps));
        }
        return result;
    }

    private Map<String, Object> buildConflict(String code,
                                              String module,
                                              List<String> relatedModules,
                                              String reason) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("module", module);
        row.put("moduleName", getModuleName(module));
        row.put("relatedModules", relatedModules == null ? Collections.emptyList() : relatedModules);
        row.put("relatedModuleNames", resolveModuleNames(relatedModules));
        row.put("reason", reason);
        return row;
    }

    private List<String> resolveModuleNames(List<String> modules) {
        if (modules == null || modules.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (String module : modules) {
            names.add(getModuleName(module));
        }
        return names;
    }

    private Map<String, Object> buildBlockedResult(String message, Object conflicts) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("blocked", true);
        result.put("message", message);
        result.put("conflicts", conflicts);
        result.put("snapshot", getAllSwitches());
        result.put("summary", getSwitchSummary());
        return result;
    }

    private Map<String, Object> buildAppliedResult(Map<String, Boolean> changed,
                                                   List<String> autoEnabledModules,
                                                   List<String> cascadedDisabledModules,
                                                   List<Map<String, Object>> warnings,
                                                   String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("blocked", false);
        result.put("message", message);
        result.put("changed", changed);
        result.put("changedCount", changed == null ? 0 : changed.size());
        result.put("autoEnabledModules", autoEnabledModules == null ? Collections.emptyList() : autoEnabledModules);
        result.put("cascadedDisabledModules", cascadedDisabledModules == null ? Collections.emptyList() : cascadedDisabledModules);
        result.put("warnings", warnings == null ? Collections.emptyList() : warnings);
        result.put("snapshot", getAllSwitches());
        result.put("summary", getSwitchSummary());
        return result;
    }
}
