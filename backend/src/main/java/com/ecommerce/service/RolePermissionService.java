package com.ecommerce.service;

import com.alibaba.fastjson2.JSON;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Constants;
import com.ecommerce.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RolePermissionService {

    private static final String ROLE_PERMISSION_KEY = "system:role:permission:v1";

    private static final LinkedHashMap<String, PermissionDef> CATALOG = new LinkedHashMap<>();
    private static final LinkedHashMap<String, RoleDef> ROLE_META = new LinkedHashMap<>();
    private static final LinkedHashMap<String, LinkedHashSet<String>> DEFAULT_ROLE_PERMISSIONS = new LinkedHashMap<>();
    private static final LinkedHashSet<String> ADMIN_PROTECTED_PERMISSIONS = new LinkedHashSet<>(
            Arrays.asList("system.role.read", "system.role.write"));

    static {
        // 核心运营
        addPermission("dashboard.view", "仪表盘查看", "core", "核心运营", "查看系统核心运营指标", "low");
        addPermission("product.manage", "商品管理", "core", "核心运营", "管理商品、分类与上下架", "medium");
        addPermission("order.manage", "订单管理", "core", "核心运营", "管理订单状态与履约流程", "high");
        addPermission("refund.manage", "退款管理", "core", "核心运营", "审核退款与售后处理", "high");
        addPermission("review.manage", "评价管理", "core", "核心运营", "审核评价与回复管理", "medium");
        addPermission("merchant.manage", "商家管理", "core", "核心运营", "商家账户与经营资料管理", "high");
        addPermission("user.manage", "用户管理", "core", "核心运营", "用户状态与资料审核管理", "high");
        addPermission("customer.service.manage", "客服工单", "core", "核心运营", "客服工作台与工单分配", "high");

        // 分析与推荐
        addPermission("analysis.view", "分析看板", "analytics", "分析与推荐", "查看销售、行为、分群等分析看板", "medium");
        addPermission("stream.monitor", "实时流监控", "analytics", "分析与推荐", "查看实时热榜与链路告警", "high");
        addPermission("recommend.manage", "推荐系统管理", "analytics", "分析与推荐", "管理推荐实验与策略预览", "high");

        // 系统治理
        addPermission("system.module.read", "功能开关查看", "system", "系统治理", "查看模块开关与依赖关系", "medium");
        addPermission("system.module.write", "功能开关修改", "system", "系统治理", "修改模块开关、批量编排", "critical");
        addPermission("system.message.manage", "系统消息推送", "system", "系统治理", "广播系统通知消息", "high");
        addPermission("system.log.view", "操作日志查看", "system", "系统治理", "查看后台操作日志", "medium");
        addPermission("system.role.read", "角色权限查看", "system", "系统治理", "查看角色权限矩阵", "high");
        addPermission("system.role.write", "角色权限修改", "system", "系统治理", "修改角色权限与恢复默认", "critical");

        // 商家能力
        addPermission("merchant.dashboard.view", "商家仪表盘", "merchant", "商家能力", "查看商家经营概览", "low");
        addPermission("merchant.product.manage", "商家商品管理", "merchant", "商家能力", "管理店铺商品与活动提报", "medium");
        addPermission("merchant.order.manage", "商家订单处理", "merchant", "商家能力", "商家订单履约与退款处理", "high");
        addPermission("merchant.finance.view", "商家财务查看", "merchant", "商家能力", "查看商家财务与钱包数据", "medium");

        ROLE_META.put(Constants.Role.ADMIN, new RoleDef(Constants.Role.ADMIN, "平台管理员", true));
        ROLE_META.put(Constants.Role.MERCHANT, new RoleDef(Constants.Role.MERCHANT, "商家账号", true));
        ROLE_META.put(Constants.Role.USER, new RoleDef(Constants.Role.USER, "普通用户", false));

        LinkedHashSet<String> adminPermissions = new LinkedHashSet<>(CATALOG.keySet());
        adminPermissions.addAll(ADMIN_PROTECTED_PERMISSIONS);
        DEFAULT_ROLE_PERMISSIONS.put(Constants.Role.ADMIN, adminPermissions);

        LinkedHashSet<String> merchantPermissions = new LinkedHashSet<>(Arrays.asList(
                "merchant.dashboard.view",
                "merchant.product.manage",
                "merchant.order.manage",
                "merchant.finance.view"
        ));
        DEFAULT_ROLE_PERMISSIONS.put(Constants.Role.MERCHANT, merchantPermissions);

        DEFAULT_ROLE_PERMISSIONS.put(Constants.Role.USER, new LinkedHashSet<String>());
    }

    @Autowired
    private RedisUtil redisUtil;

    public Map<String, Object> getRolePermissionOverview() {
        Map<String, LinkedHashSet<String>> rolePermissions = loadRolePermissions();
        List<Map<String, Object>> roleList = new ArrayList<>();

        for (Map.Entry<String, RoleDef> entry : ROLE_META.entrySet()) {
            String role = entry.getKey();
            RoleDef roleDef = entry.getValue();
            LinkedHashSet<String> current = rolePermissions.getOrDefault(role, new LinkedHashSet<String>());
            LinkedHashSet<String> defaults = DEFAULT_ROLE_PERMISSIONS.getOrDefault(role, new LinkedHashSet<String>());

            Map<String, Object> roleRow = new LinkedHashMap<>();
            roleRow.put("role", role);
            roleRow.put("roleName", roleDef.roleName);
            roleRow.put("editable", roleDef.editable);
            roleRow.put("permissions", new ArrayList<>(current));
            roleRow.put("defaultPermissions", new ArrayList<>(defaults));
            roleRow.put("grantedCount", current.size());
            roleRow.put("totalCount", CATALOG.size());
            roleList.add(roleRow);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roles", roleList);
        result.put("catalog", buildCatalogRows());
        result.put("protectedPermissions", new ArrayList<>(ADMIN_PROTECTED_PERMISSIONS));
        result.put("totalPermissionCount", CATALOG.size());
        return result;
    }

    public Map<String, Object> getCurrentRolePermissionView(String role) {
        String normalizedRole = normalizeRole(role);
        LinkedHashSet<String> permissions = getPermissionsByRole(normalizedRole);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", normalizedRole);
        result.put("permissions", new ArrayList<>(permissions));
        result.put("permissionCount", permissions.size());
        return result;
    }

    public Map<String, Object> updateRolePermissions(String role, List<String> permissionList) {
        String normalizedRole = normalizeRole(role);
        RoleDef roleDef = ROLE_META.get(normalizedRole);
        if (roleDef == null) {
            throw BusinessException.badRequest("不支持的角色: " + role);
        }
        if (!roleDef.editable) {
            throw BusinessException.badRequest("该角色不支持在线修改权限");
        }

        LinkedHashSet<String> normalizedPermissions = normalizePermissions(permissionList);
        if (Constants.Role.ADMIN.equals(normalizedRole)) {
            normalizedPermissions.addAll(ADMIN_PROTECTED_PERMISSIONS);
        }

        Map<String, LinkedHashSet<String>> rolePermissions = loadRolePermissions();
        rolePermissions.put(normalizedRole, normalizedPermissions);
        saveRolePermissions(rolePermissions);
        return buildRoleRow(normalizedRole, normalizedPermissions);
    }

    public Map<String, Object> resetRolePermissions(String role) {
        String normalizedRole = normalizeRole(role);
        RoleDef roleDef = ROLE_META.get(normalizedRole);
        if (roleDef == null) {
            throw BusinessException.badRequest("不支持的角色: " + role);
        }
        if (!roleDef.editable) {
            throw BusinessException.badRequest("该角色不支持重置权限");
        }

        Map<String, LinkedHashSet<String>> rolePermissions = loadRolePermissions();
        LinkedHashSet<String> defaults = new LinkedHashSet<>(
                DEFAULT_ROLE_PERMISSIONS.getOrDefault(normalizedRole, new LinkedHashSet<String>()));
        if (Constants.Role.ADMIN.equals(normalizedRole)) {
            defaults.addAll(ADMIN_PROTECTED_PERMISSIONS);
        }
        rolePermissions.put(normalizedRole, defaults);
        saveRolePermissions(rolePermissions);
        return buildRoleRow(normalizedRole, defaults);
    }

    public boolean hasPermission(String role, String permission) {
        if (!StringUtils.hasText(permission)) {
            return true;
        }
        String normalizedRole = normalizeRole(role);
        LinkedHashSet<String> permissions = getPermissionsByRole(normalizedRole);
        return permissions.contains(permission.trim());
    }

    public LinkedHashSet<String> getPermissionsByRole(String role) {
        String normalizedRole = normalizeRole(role);
        Map<String, LinkedHashSet<String>> rolePermissions = loadRolePermissions();
        LinkedHashSet<String> permissions = rolePermissions.get(normalizedRole);
        if (permissions == null) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(permissions);
    }

    private Map<String, Object> buildRoleRow(String role, LinkedHashSet<String> permissions) {
        RoleDef roleDef = ROLE_META.get(role);
        LinkedHashSet<String> defaults = DEFAULT_ROLE_PERMISSIONS.getOrDefault(role, new LinkedHashSet<String>());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("role", role);
        row.put("roleName", roleDef == null ? role : roleDef.roleName);
        row.put("editable", roleDef != null && roleDef.editable);
        row.put("permissions", new ArrayList<>(permissions));
        row.put("defaultPermissions", new ArrayList<>(defaults));
        row.put("grantedCount", permissions.size());
        row.put("totalCount", CATALOG.size());
        return row;
    }

    private List<Map<String, Object>> buildCatalogRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PermissionDef def : CATALOG.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", def.key);
            row.put("name", def.name);
            row.put("group", def.group);
            row.put("groupName", def.groupName);
            row.put("description", def.description);
            row.put("riskLevel", def.riskLevel);
            rows.add(row);
        }
        return rows;
    }

    private Map<String, LinkedHashSet<String>> loadRolePermissions() {
        Map<String, LinkedHashSet<String>> merged = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> entry : DEFAULT_ROLE_PERMISSIONS.entrySet()) {
            merged.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }

        Object raw = redisUtil.get(ROLE_PERMISSION_KEY);
        if (raw == null) {
            return merged;
        }

        Map<String, Object> storedMap = null;
        if (raw instanceof Map<?, ?>) {
            storedMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                storedMap.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        } else {
            try {
                storedMap = JSON.parseObject(String.valueOf(raw));
            } catch (Exception ignored) {
                storedMap = null;
            }
        }
        if (storedMap == null || storedMap.isEmpty()) {
            return merged;
        }

        for (Map.Entry<String, Object> entry : storedMap.entrySet()) {
            String role = normalizeRole(entry.getKey());
            if (!ROLE_META.containsKey(role)) {
                continue;
            }
            List<String> values;
            Object value = entry.getValue();
            if (value instanceof List<?>) {
                values = new ArrayList<>();
                for (Object item : (List<?>) value) {
                    if (item != null) {
                        values.add(String.valueOf(item));
                    }
                }
            } else {
                values = Collections.emptyList();
            }
            LinkedHashSet<String> normalized = normalizePermissions(values);
            if (Constants.Role.ADMIN.equals(role)) {
                normalized.addAll(ADMIN_PROTECTED_PERMISSIONS);
            }
            merged.put(role, normalized);
        }
        return merged;
    }

    private void saveRolePermissions(Map<String, LinkedHashSet<String>> rolePermissions) {
        Map<String, List<String>> payload = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> entry : rolePermissions.entrySet()) {
            payload.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        redisUtil.set(ROLE_PERMISSION_KEY, JSON.toJSONString(payload));
    }

    private LinkedHashSet<String> normalizePermissions(List<String> permissions) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (permissions == null) {
            return normalized;
        }
        for (String permission : permissions) {
            if (!StringUtils.hasText(permission)) {
                continue;
            }
            String key = permission.trim();
            if (!CATALOG.containsKey(key)) {
                continue;
            }
            normalized.add(key);
        }
        return normalized;
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toLowerCase();
    }

    private static void addPermission(String key,
                                      String name,
                                      String group,
                                      String groupName,
                                      String description,
                                      String riskLevel) {
        CATALOG.put(key, new PermissionDef(key, name, group, groupName, description, riskLevel));
    }

    private static class RoleDef {
        final String role;
        final String roleName;
        final boolean editable;

        RoleDef(String role, String roleName, boolean editable) {
            this.role = role;
            this.roleName = roleName;
            this.editable = editable;
        }
    }

    private static class PermissionDef {
        final String key;
        final String name;
        final String group;
        final String groupName;
        final String description;
        final String riskLevel;

        PermissionDef(String key,
                      String name,
                      String group,
                      String groupName,
                      String description,
                      String riskLevel) {
            this.key = key;
            this.name = name;
            this.group = group;
            this.groupName = groupName;
            this.description = description;
            this.riskLevel = riskLevel;
        }
    }
}
