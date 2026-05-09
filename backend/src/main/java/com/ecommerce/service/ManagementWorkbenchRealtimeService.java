package com.ecommerce.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.common.Constants;
import com.ecommerce.entity.User;
import com.ecommerce.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ManagementWorkbenchRealtimeService {

    private static final String USER_WORKBENCH_DESTINATION = "/queue/workbench-refresh";
    private static final String USER_IM_DESTINATION = "/queue/im-refresh";
    private static final String USER_MESSAGE_DESTINATION = "/queue/user-message-refresh";
    private static final String ADMIN_ANALYSIS_DESTINATION = "/topic/admin/analysis/refresh";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ManagementWorkbenchBadgeService managementWorkbenchBadgeService;

    public void notifyAdmins(String event) {
        notifyAdmins(event, Collections.emptyMap());
    }

    public void notifyAdmins(String event, Map<String, Object> extra) {
        List<User> admins = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, Constants.Role.ADMIN)
                        .eq(User::getStatus, 1)
                        .select(User::getId)
        );
        Set<Long> adminIds = new LinkedHashSet<>();
        for (User admin : admins) {
            if (admin != null && admin.getId() != null && admin.getId() > 0) {
                adminIds.add(admin.getId());
            }
        }
        notifyUsers(adminIds, "admin", event, enrichExtraForAdmin(extra));
    }

    public void notifyMerchant(Long merchantId, String event) {
        notifyMerchant(merchantId, event, Collections.emptyMap());
    }

    public void notifyMerchant(Long merchantId, String event, Map<String, Object> extra) {
        notifyUser(merchantId, Constants.Role.MERCHANT, event, enrichExtraForMerchant(merchantId, extra));
    }

    public void notifyMerchantMessageChanged(Long userId, String event) {
        notifyMerchantMessageChanged(userId, event, Collections.emptyMap());
    }

    public void notifyMerchantMessageChanged(Long userId, String event, Map<String, Object> extra) {
        User user = userId == null ? null : userMapper.selectById(userId);
        if (user == null || user.getId() == null || user.getStatus() == null || user.getStatus() != 1) {
            return;
        }
        if (!Constants.Role.MERCHANT.equalsIgnoreCase(String.valueOf(user.getRole()))) {
            return;
        }
        Map<String, Object> messageExtra = cloneExtra(extra);
        if (!messageExtra.containsKey("scope")) {
            messageExtra.put("scope", ManagementWorkbenchBadgeService.SCOPE_MESSAGE);
        }
        notifyUser(user.getId(), Constants.Role.MERCHANT, event, enrichExtraForMerchant(user.getId(), messageExtra));
    }

    public void notifyUsers(Collection<Long> userIds, String role, String event, Map<String, Object> extra) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Set<Long> deduped = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId != null && userId > 0) {
                deduped.add(userId);
            }
        }
        for (Long userId : deduped) {
            notifyUser(userId, role, event, extra);
        }
    }

    public void notifyUser(Long userId, String role, String event, Map<String, Object> extra) {
        sendToUserDestination(userId, USER_WORKBENCH_DESTINATION, role, event, extra);
    }

    public void notifyImUser(Long userId, String role, String event, Map<String, Object> extra) {
        sendToUserDestination(userId, USER_IM_DESTINATION, role, event, extra);
    }

    public void notifyUserMessageChanged(Long userId, String event) {
        notifyUserMessageChanged(userId, event, Collections.emptyMap());
    }

    public void notifyUserMessageChanged(Long userId, String event, Map<String, Object> extra) {
        sendToUserDestination(userId, USER_MESSAGE_DESTINATION, Constants.Role.USER, event, extra);
    }

    public void notifyImAdmins(String event, Map<String, Object> extra) {
        List<User> admins = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, Constants.Role.ADMIN)
                        .eq(User::getStatus, 1)
                        .select(User::getId)
        );
        for (User admin : admins) {
            if (admin != null && admin.getId() != null && admin.getId() > 0) {
                notifyImUser(admin.getId(), Constants.Role.ADMIN, event, extra);
            }
        }
    }

    public void notifyAdminAnalysisRefresh(String event, Map<String, Object> extra) {
        if (!StringUtils.hasText(event)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event.trim());
        payload.put("timestamp", LocalDateTime.now().format(DATE_TIME_FORMATTER));
        if (extra != null && !extra.isEmpty()) {
            payload.putAll(extra);
        }
        messagingTemplate.convertAndSend(ADMIN_ANALYSIS_DESTINATION, payload);
    }

    private Map<String, Object> enrichExtraForAdmin(Map<String, Object> extra) {
        Map<String, Object> payloadExtra = cloneExtra(extra);
        if (payloadExtra.containsKey("counts")) {
            return payloadExtra;
        }
        String scope = resolveScope(payloadExtra);
        if (!StringUtils.hasText(scope)) {
            return payloadExtra;
        }
        payloadExtra.put("counts", managementWorkbenchBadgeService.getAdminBadgeCounts(Collections.singleton(scope)));
        return payloadExtra;
    }

    private Map<String, Object> enrichExtraForMerchant(Long merchantId, Map<String, Object> extra) {
        Map<String, Object> payloadExtra = cloneExtra(extra);
        if (payloadExtra.containsKey("counts")) {
            return payloadExtra;
        }
        String scope = resolveScope(payloadExtra);
        if (!StringUtils.hasText(scope)) {
            return payloadExtra;
        }
        payloadExtra.put("counts", managementWorkbenchBadgeService.getMerchantBadgeCounts(
                merchantId,
                Collections.singleton(scope)));
        return payloadExtra;
    }

    private Map<String, Object> cloneExtra(Map<String, Object> extra) {
        if (extra == null || extra.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(extra);
    }

    private void sendToUserDestination(Long userId,
                                       String destination,
                                       String role,
                                       String event,
                                       Map<String, Object> extra) {
        if (userId == null || userId <= 0 || !StringUtils.hasText(event) || !StringUtils.hasText(destination)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event.trim());
        payload.put("role", StringUtils.hasText(role) ? role.trim() : null);
        payload.put("timestamp", LocalDateTime.now().format(DATE_TIME_FORMATTER));
        if (extra != null && !extra.isEmpty()) {
            payload.putAll(extra);
        }
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), destination, payload);
    }

    private String resolveScope(Map<String, Object> extra) {
        if (extra == null || extra.isEmpty()) {
            return null;
        }
        Object rawScope = extra.get("scope");
        if (rawScope == null) {
            return null;
        }
        List<String> scopes = managementWorkbenchBadgeService.parseScopes(String.valueOf(rawScope));
        if (scopes.isEmpty()) {
            return null;
        }
        return scopes.get(0);
    }
}
