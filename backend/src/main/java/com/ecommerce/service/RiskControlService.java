package com.ecommerce.service;

import com.alibaba.fastjson2.JSON;
import com.ecommerce.common.RiskBlacklistRecord;
import com.ecommerce.common.RiskControlRule;
import com.ecommerce.common.RiskDecision;
import com.ecommerce.config.RiskControlProperties;
import com.ecommerce.utils.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class RiskControlService {

    private static final Logger log = LoggerFactory.getLogger(RiskControlService.class);

    private static final String RULE_CUSTOM_HASH_KEY = "risk:rule:custom";
    private static final String BLACKLIST_INDEX_PREFIX = "risk:ban:index:";
    private static final String BLACKLIST_KEY_PREFIX = "risk:ban:";
    private static final String COUNTER_KEY_PREFIX = "risk:counter:";
    private static final String VIOLATION_KEY_PREFIX = "risk:violation:";

    private static final String METRIC_GLOBAL_TOTAL = "risk:metric:global:total";
    private static final String METRIC_GLOBAL_LIMITED = "risk:metric:global:limited";
    private static final String METRIC_GLOBAL_BLACKLIST = "risk:metric:global:blacklist";
    private static final String METRIC_ROUTE_INDEX = "risk:metric:route:index";
    private static final String METRIC_ROUTE_PREFIX = "risk:metric:route:";

    private static final long METRIC_EXPIRE_DAYS = 30;
    private static final String SUBJECT_IP = "IP";
    private static final String SUBJECT_USER = "USER";
    private static final String SUBJECT_DEVICE = "DEVICE";

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> LIMIT_SCRIPT;

    static {
        LIMIT_SCRIPT = new DefaultRedisScript<>();
        LIMIT_SCRIPT.setScriptText(
                "local current = redis.call('INCRBY', KEYS[1], ARGV[1])\n" +
                "if current == tonumber(ARGV[1]) then\n" +
                "  redis.call('EXPIRE', KEYS[1], ARGV[2])\n" +
                "end\n" +
                "local ttl = redis.call('TTL', KEYS[1])\n" +
                "if current > tonumber(ARGV[3]) then\n" +
                "  return {0, current, ttl}\n" +
                "end\n" +
                "return {1, current, ttl}\n"
        );
        LIMIT_SCRIPT.setResultType(List.class);
    }

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RiskControlProperties properties;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Map<String, RiskControlRule> defaultRules = new LinkedHashMap<>();

    @PostConstruct
    public void initDefaultRules() {
        defaultRules.put("auth_send_code", buildRule(
                "auth_send_code", "发送验证码", "POST", "/api/auth/send-code",
                SUBJECT_IP, 60, 3, 6, 1800, 600,
                "限制验证码接口被恶意刷取"
        ));
        defaultRules.put("auth_login", buildRule(
                "auth_login", "用户登录", "POST", "/api/auth/login",
                SUBJECT_IP, 60, 20, 12, 1800, 600,
                "限制登录接口暴力尝试"
        ));
        defaultRules.put("auth_register", buildRule(
                "auth_register", "用户注册", "POST", "/api/auth/register",
                SUBJECT_IP, 60, 10, 8, 1800, 600,
                "限制批量注册行为"
        ));
        defaultRules.put("ai_chat", buildRule(
                "ai_chat", "AI购物助手", "POST", "/api/ai/chat",
                SUBJECT_USER, 60, 12, 8, 900, 600,
                "控制AI高频请求和成本异常"
        ));
        defaultRules.put("ai_product_qa", buildRule(
                "ai_product_qa", "商品AI问答", "POST", "/api/ai/product-qa",
                SUBJECT_IP, 60, 40, 20, 600, 600,
                "限制公开AI问答接口被刷"
        ));
        defaultRules.put("im_send_message", buildRule(
                "im_send_message", "IM发消息", "POST", "/api/im/conversations/*/messages",
                SUBJECT_USER, 60, 30, 15, 900, 600,
                "限制IM高频发言与恶意灌水"
        ));
        defaultRules.put("order_create", buildRule(
                "order_create", "普通下单", "POST", "/api/orders",
                SUBJECT_USER, 30, 6, 8, 1200, 900,
                "限制重复下单和脚本刷单"
        ));
        defaultRules.put("seckill_order_create", buildRule(
                "seckill_order_create", "秒杀下单", "POST", "/api/seckill/orders",
                SUBJECT_USER, 10, 3, 6, 1800, 900,
                "限制秒杀恶意抢单"
        ));
        defaultRules.put("merchant_ai_chat", buildRule(
                "merchant_ai_chat", "商家AI助手", "POST", "/api/merchant/ai/chat",
                SUBJECT_USER, 60, 20, 10, 900, 600,
                "限制商家AI接口异常调用"
        ));
    }

    public RiskDecision evaluate(HttpServletRequest request) {
        if (request == null || !properties.isEnabled()) {
            return RiskDecision.allow();
        }
        String method = normalizeMethod(request.getMethod());
        String path = normalizePath(request.getRequestURI());
        String ip = resolveClientIp(request);
        Long userId = resolveUserId(request);
        String deviceId = resolveDeviceId(request);

        RiskControlRule matchedRule = matchRule(method, path);
        String routeId = matchedRule != null ? matchedRule.getRouteId() : path;

        RiskBlacklistRecord blacklistHit = findBlacklistHit(ip, userId, deviceId);
        if (blacklistHit != null) {
            recordBlacklistMetric(routeId);
            RiskDecision denied = new RiskDecision();
            denied.setAllow(false);
            denied.setReasonCode("BLACKLIST");
            denied.setHttpStatus(403);
            denied.setRouteId(routeId);
            denied.setSubjectType(blacklistHit.getSubjectType());
            denied.setSubjectValue(blacklistHit.getSubjectValue());
            denied.setMessage(StringUtils.hasText(blacklistHit.getReason())
                    ? "访问受限：" + blacklistHit.getReason()
                    : "当前账号或IP处于风控限制中");
            denied.setRetryAfterSeconds(0L);
            return denied;
        }

        if (matchedRule == null || !Boolean.TRUE.equals(matchedRule.getEnabled())) {
            return RiskDecision.allow();
        }

        recordTotalMetric(routeId);
        String subjectValue = resolveSubjectValue(matchedRule.getSubjectType(), ip, userId, deviceId);
        if (!StringUtils.hasText(subjectValue)) {
            return RiskDecision.allow();
        }

        LimitResult limitResult = consumeLimit(matchedRule, subjectValue);
        if (limitResult.allowed) {
            return RiskDecision.allow();
        }

        recordLimitedMetric(routeId);
        long violationCount = increaseViolationCount(matchedRule, subjectValue);
        boolean banTriggered = violationCount >= safePositive(matchedRule.getBanThreshold(), 6);
        if (banTriggered) {
            addBlacklist(
                    matchedRule.getSubjectType(),
                    subjectValue,
                    matchedRule.getBanDurationSeconds(),
                    "自动封禁：连续触发限流阈值",
                    "AUTO",
                    "risk-engine",
                    matchedRule.getRouteId()
            );
            recordBlacklistMetric(routeId);
        }

        RiskDecision denied = new RiskDecision();
        denied.setAllow(false);
        denied.setReasonCode(banTriggered ? "BLACKLIST" : "RATE_LIMIT");
        denied.setHttpStatus(banTriggered ? 403 : 429);
        denied.setRouteId(routeId);
        denied.setSubjectType(matchedRule.getSubjectType());
        denied.setSubjectValue(subjectValue);
        denied.setRetryAfterSeconds(limitResult.retryAfterSeconds > 0
                ? limitResult.retryAfterSeconds
                : (long) safePositive(matchedRule.getWindowSeconds(), 60));
        denied.setMessage(banTriggered
                ? "请求异常，已触发临时风控限制，请稍后再试"
                : "操作过于频繁，请稍后再试");
        return denied;
    }

    public List<RiskControlRule> listRules() {
        Map<String, RiskControlRule> effective = loadEffectiveRules();
        List<RiskControlRule> rules = new ArrayList<>(effective.values());
        rules.sort(Comparator.comparing(RiskControlRule::getRouteId));
        return rules;
    }

    public RiskControlRule updateRule(String routeId, Map<String, Object> payload, String operator) {
        if (!StringUtils.hasText(routeId) || !defaultRules.containsKey(routeId)) {
            throw new IllegalArgumentException("无效的 routeId: " + routeId);
        }

        RiskControlRule base = loadEffectiveRules().get(routeId);
        if (base == null) {
            base = defaultRules.get(routeId).copy();
        }
        RiskControlRule updated = base.copy();
        applyRulePatch(updated, payload);
        updated.setCustomized(true);
        updated.normalize();

        redisTemplate.opsForHash().put(RULE_CUSTOM_HASH_KEY, routeId, JSON.toJSONString(updated));
        log.info("[RiskControl] 更新规则 routeId={}, operator={}, rule={}", routeId, operator, JSON.toJSONString(updated));
        return updated;
    }

    public RiskControlRule resetRule(String routeId, String operator) {
        if (!StringUtils.hasText(routeId) || !defaultRules.containsKey(routeId)) {
            throw new IllegalArgumentException("无效的 routeId: " + routeId);
        }
        redisTemplate.opsForHash().delete(RULE_CUSTOM_HASH_KEY, routeId);
        RiskControlRule reset = defaultRules.get(routeId).copy();
        reset.setCustomized(false);
        log.info("[RiskControl] 重置规则 routeId={}, operator={}", routeId, operator);
        return reset;
    }

    public RiskBlacklistRecord addBlacklist(String subjectType,
                                            String subjectValue,
                                            Integer durationSeconds,
                                            String reason,
                                            String source,
                                            String operator,
                                            String routeId) {
        String normalizedType = normalizeSubjectType(subjectType);
        String normalizedValue = normalizeSubjectValue(subjectValue);
        if (!StringUtils.hasText(normalizedValue)) {
            throw new IllegalArgumentException("黑名单对象不能为空");
        }
        int duration = durationSeconds == null ? 0 : durationSeconds;

        LocalDateTime now = LocalDateTime.now();
        RiskBlacklistRecord record = new RiskBlacklistRecord();
        record.setSubjectType(normalizedType);
        record.setSubjectValue(normalizedValue);
        record.setReason(StringUtils.hasText(reason) ? reason.trim() : "触发风控策略");
        record.setSource(StringUtils.hasText(source) ? source.trim().toUpperCase() : "MANUAL");
        record.setOperator(StringUtils.hasText(operator) ? operator.trim() : "system");
        record.setRouteId(StringUtils.hasText(routeId) ? routeId.trim() : null);
        record.setCreatedAt(now.format(DATE_TIME_FORMATTER));
        record.setPermanent(duration <= 0);
        record.setExpireAt(duration <= 0 ? null : now.plusSeconds(duration).format(DATE_TIME_FORMATTER));

        String key = blacklistKey(normalizedType, normalizedValue);
        String payload = JSON.toJSONString(record);
        if (duration <= 0) {
            redisUtil.set(key, payload);
        } else {
            redisUtil.set(key, payload, duration, TimeUnit.SECONDS);
        }
        redisUtil.addToSet(blacklistIndexKey(normalizedType), encodeValue(normalizedValue));
        return record;
    }

    public boolean removeBlacklist(String subjectType, String subjectValue) {
        String normalizedType = normalizeSubjectType(subjectType);
        String normalizedValue = normalizeSubjectValue(subjectValue);
        if (!StringUtils.hasText(normalizedValue)) {
            return false;
        }
        String key = blacklistKey(normalizedType, normalizedValue);
        Boolean deleted = redisUtil.delete(key);
        try {
            redisTemplate.opsForSet().remove(blacklistIndexKey(normalizedType), encodeValue(normalizedValue));
        } catch (Exception e) {
            log.debug("[RiskControl] 黑名单索引清理失败 type={}, value={}, err={}",
                    normalizedType, normalizedValue, e.getMessage());
        }
        return Boolean.TRUE.equals(deleted);
    }

    public void resetRouteSubjectState(String routeId, String subjectType, String subjectValue) {
        String safeRouteId = StringUtils.hasText(routeId) ? routeId.trim() : null;
        String normalizedType = normalizeSubjectType(subjectType);
        String normalizedValue = normalizeSubjectValue(subjectValue);
        if (!StringUtils.hasText(safeRouteId) || !StringUtils.hasText(normalizedValue)) {
            return;
        }

        removeBlacklist(normalizedType, normalizedValue);
        String encodedValue = encodeValue(normalizedValue);
        redisUtil.delete(COUNTER_KEY_PREFIX + safeRouteId + ":" + normalizedType + ":" + encodedValue);
        redisUtil.delete(VIOLATION_KEY_PREFIX + safeRouteId + ":" + normalizedType + ":" + encodedValue);
    }

    public Map<String, Object> listBlacklist(String subjectType, String keyword, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase() : null;

        List<RiskBlacklistRecord> allRecords = new ArrayList<>();
        if (StringUtils.hasText(subjectType) && !"ALL".equalsIgnoreCase(subjectType.trim())) {
            allRecords.addAll(listByType(normalizeSubjectType(subjectType), normalizedKeyword));
        } else {
            allRecords.addAll(listByType(SUBJECT_IP, normalizedKeyword));
            allRecords.addAll(listByType(SUBJECT_USER, normalizedKeyword));
            allRecords.addAll(listByType(SUBJECT_DEVICE, normalizedKeyword));
        }

        allRecords.sort((a, b) -> safeString(b.getCreatedAt()).compareTo(safeString(a.getCreatedAt())));
        int total = allRecords.size();
        int from = Math.min((safePage - 1) * safeSize, total);
        int to = Math.min(from + safeSize, total);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", allRecords.subList(from, to));
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        long total = readLong(redisUtil.get(METRIC_GLOBAL_TOTAL));
        long limited = readLong(redisUtil.get(METRIC_GLOBAL_LIMITED));
        long blacklist = readLong(redisUtil.get(METRIC_GLOBAL_BLACKLIST));

        result.put("enabled", properties.isEnabled());
        result.put("mode", properties.resolveMode());
        result.put("totalRequests", total);
        result.put("limitedRequests", limited);
        result.put("blacklistHits", blacklist);
        result.put("limitRate", total == 0 ? 0D : roundPercent(limited, total));
        result.put("activeRules", listRules().stream().filter(r -> Boolean.TRUE.equals(r.getEnabled())).count());

        Map<String, Object> blacklistSummary = new LinkedHashMap<>();
        blacklistSummary.put("ip", countActiveBlacklist(SUBJECT_IP));
        blacklistSummary.put("user", countActiveBlacklist(SUBJECT_USER));
        blacklistSummary.put("device", countActiveBlacklist(SUBJECT_DEVICE));
        result.put("blacklistSummary", blacklistSummary);
        result.put("topLimitedRoutes", topLimitedRoutes());
        return result;
    }

    private List<Map<String, Object>> topLimitedRoutes() {
        Set<Object> routes;
        try {
            routes = redisTemplate.opsForSet().members(METRIC_ROUTE_INDEX);
        } catch (Exception e) {
            return Collections.emptyList();
        }
        if (routes == null || routes.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object routeObj : routes) {
            String routeId = routeObj == null ? null : routeObj.toString();
            if (!StringUtils.hasText(routeId)) {
                continue;
            }
            long total = readLong(redisUtil.get(routeMetricKey(routeId, "total")));
            long limited = readLong(redisUtil.get(routeMetricKey(routeId, "limited")));
            long blacklist = readLong(redisUtil.get(routeMetricKey(routeId, "blacklist")));

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("routeId", routeId);
            item.put("total", total);
            item.put("limited", limited);
            item.put("blacklist", blacklist);
            item.put("limitRate", total == 0 ? 0D : roundPercent(limited, total));
            items.add(item);
        }
        items.sort((a, b) -> Long.compare(readLong(b.get("limited")), readLong(a.get("limited"))));
        if (items.size() > 10) {
            return new ArrayList<>(items.subList(0, 10));
        }
        return items;
    }

    private List<RiskBlacklistRecord> listByType(String subjectType, String keywordLower) {
        Set<Object> members;
        try {
            members = redisTemplate.opsForSet().members(blacklistIndexKey(subjectType));
        } catch (Exception e) {
            log.debug("[RiskControl] 读取黑名单索引失败 type={}, err={}", subjectType, e.getMessage());
            return Collections.emptyList();
        }
        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }

        List<RiskBlacklistRecord> records = new ArrayList<>();
        for (Object member : members) {
            String encoded = member == null ? null : member.toString();
            if (!StringUtils.hasText(encoded)) {
                continue;
            }
            RiskBlacklistRecord record = loadRecordByEncoded(subjectType, encoded);
            if (record == null) {
                try {
                    redisTemplate.opsForSet().remove(blacklistIndexKey(subjectType), encoded);
                } catch (Exception ignore) {
                    // ignore
                }
                continue;
            }
            if (StringUtils.hasText(keywordLower)) {
                String union = (safeString(record.getSubjectValue()) + " " + safeString(record.getReason())).toLowerCase();
                if (!union.contains(keywordLower)) {
                    continue;
                }
            }
            records.add(record);
        }
        return records;
    }

    private long countActiveBlacklist(String subjectType) {
        return listByType(subjectType, null).size();
    }

    private void applyRulePatch(RiskControlRule rule, Map<String, Object> payload) {
        if (rule == null || payload == null || payload.isEmpty()) {
            return;
        }
        if (payload.containsKey("enabled")) {
            rule.setEnabled(parseBoolean(payload.get("enabled"), rule.getEnabled()));
        }
        if (payload.containsKey("subjectType")) {
            rule.setSubjectType(normalizeSubjectType(asString(payload.get("subjectType"))));
        }
        if (payload.containsKey("windowSeconds")) {
            rule.setWindowSeconds(parseInt(payload.get("windowSeconds"), rule.getWindowSeconds()));
        }
        if (payload.containsKey("maxRequests")) {
            rule.setMaxRequests(parseInt(payload.get("maxRequests"), rule.getMaxRequests()));
        }
        if (payload.containsKey("banThreshold")) {
            rule.setBanThreshold(parseInt(payload.get("banThreshold"), rule.getBanThreshold()));
        }
        if (payload.containsKey("banDurationSeconds")) {
            rule.setBanDurationSeconds(parseInt(payload.get("banDurationSeconds"), rule.getBanDurationSeconds()));
        }
        if (payload.containsKey("violationWindowSeconds")) {
            rule.setViolationWindowSeconds(parseInt(payload.get("violationWindowSeconds"), rule.getViolationWindowSeconds()));
        }
        if (payload.containsKey("description")) {
            String description = asString(payload.get("description"));
            if (StringUtils.hasText(description)) {
                rule.setDescription(description.trim());
            }
        }
    }

    private RiskControlRule matchRule(String method, String path) {
        Map<String, RiskControlRule> rules = loadEffectiveRules();
        for (RiskControlRule rule : rules.values()) {
            if (!Boolean.TRUE.equals(rule.getEnabled())) {
                continue;
            }
            if (!method.equalsIgnoreCase(rule.getMethod())) {
                continue;
            }
            if (pathMatcher.match(rule.getPathPattern(), path)) {
                return rule;
            }
        }
        return null;
    }

    private Map<String, RiskControlRule> loadEffectiveRules() {
        Map<String, RiskControlRule> result = new LinkedHashMap<>();
        for (Map.Entry<String, RiskControlRule> entry : defaultRules.entrySet()) {
            RiskControlRule base = entry.getValue().copy();
            base.setCustomized(false);
            result.put(entry.getKey(), base);
        }

        Map<String, RiskControlRule> custom = loadCustomRules();
        for (Map.Entry<String, RiskControlRule> entry : custom.entrySet()) {
            String routeId = entry.getKey();
            RiskControlRule customizedRule = entry.getValue();
            RiskControlRule base = result.get(routeId);
            if (base == null || customizedRule == null) {
                continue;
            }
            RiskControlRule merged = base.copy();
            mergeRule(merged, customizedRule);
            merged.setCustomized(true);
            merged.normalize();
            result.put(routeId, merged);
        }
        return result;
    }

    private Map<String, RiskControlRule> loadCustomRules() {
        Map<String, RiskControlRule> custom = new HashMap<>();
        Map<Object, Object> entries;
        try {
            entries = redisTemplate.opsForHash().entries(RULE_CUSTOM_HASH_KEY);
        } catch (Exception e) {
            log.debug("[RiskControl] 读取自定义规则失败: {}", e.getMessage());
            return custom;
        }
        if (entries == null || entries.isEmpty()) {
            return custom;
        }
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String routeId = entry.getKey().toString();
            RiskControlRule rule = parseRule(entry.getValue());
            if (rule == null) {
                continue;
            }
            rule.normalize();
            custom.put(routeId, rule);
        }
        return custom;
    }

    private RiskControlRule parseRule(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        String json = rawValue.toString();
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return JSON.parseObject(json, RiskControlRule.class);
        } catch (Exception e) {
            log.warn("[RiskControl] 解析规则失败，value={}", json);
            return null;
        }
    }

    private void mergeRule(RiskControlRule target, RiskControlRule source) {
        if (target == null || source == null) {
            return;
        }
        if (source.getEnabled() != null) target.setEnabled(source.getEnabled());
        if (StringUtils.hasText(source.getSubjectType())) target.setSubjectType(source.getSubjectType());
        if (source.getWindowSeconds() != null) target.setWindowSeconds(source.getWindowSeconds());
        if (source.getMaxRequests() != null) target.setMaxRequests(source.getMaxRequests());
        if (source.getBanThreshold() != null) target.setBanThreshold(source.getBanThreshold());
        if (source.getBanDurationSeconds() != null) target.setBanDurationSeconds(source.getBanDurationSeconds());
        if (source.getViolationWindowSeconds() != null) target.setViolationWindowSeconds(source.getViolationWindowSeconds());
        if (StringUtils.hasText(source.getDescription())) target.setDescription(source.getDescription());
    }

    private RiskBlacklistRecord findBlacklistHit(String ip, Long userId, String deviceId) {
        if (userId != null && userId > 0) {
            RiskBlacklistRecord userBan = findBlacklist(SUBJECT_USER, String.valueOf(userId));
            if (userBan != null) {
                return userBan;
            }
        }
        RiskBlacklistRecord ipBan = findBlacklist(SUBJECT_IP, ip);
        if (ipBan != null) {
            return ipBan;
        }
        if (StringUtils.hasText(deviceId)) {
            return findBlacklist(SUBJECT_DEVICE, deviceId);
        }
        return null;
    }

    private RiskBlacklistRecord findBlacklist(String subjectType, String subjectValue) {
        String normalizedType = normalizeSubjectType(subjectType);
        String normalizedValue = normalizeSubjectValue(subjectValue);
        if (!StringUtils.hasText(normalizedValue)) {
            return null;
        }
        Object payload = redisUtil.get(blacklistKey(normalizedType, normalizedValue));
        if (payload == null) {
            return null;
        }
        try {
            return JSON.parseObject(payload.toString(), RiskBlacklistRecord.class);
        } catch (Exception e) {
            log.warn("[RiskControl] 黑名单记录解析失败 type={}, value={}", normalizedType, normalizedValue);
            return null;
        }
    }

    private RiskBlacklistRecord loadRecordByEncoded(String subjectType, String encoded) {
        String value = decodeValue(encoded);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return findBlacklist(subjectType, value);
    }

    private String resolveSubjectValue(String subjectType, String ip, Long userId, String deviceId) {
        String normalizedType = normalizeSubjectType(subjectType);
        if (SUBJECT_USER.equals(normalizedType)) {
            if (userId != null && userId > 0) {
                return String.valueOf(userId);
            }
            return ip;
        }
        if (SUBJECT_DEVICE.equals(normalizedType)) {
            if (StringUtils.hasText(deviceId)) {
                return deviceId.trim();
            }
            return ip;
        }
        return ip;
    }

    private LimitResult consumeLimit(RiskControlRule rule, String subjectValue) {
        String normalizedSubject = normalizeSubjectValue(subjectValue);
        String counterKey = COUNTER_KEY_PREFIX + rule.getRouteId() + ":" + rule.getSubjectType() + ":" + encodeValue(normalizedSubject);

        LimitResult scriptResult = consumeByLua(rule, counterKey);
        if (scriptResult != null) {
            return scriptResult;
        }

        // Redis 脚本执行失败时降级为普通计数
        Long count = redisUtil.incr(counterKey, 1);
        if (count != null && count == 1L) {
            redisUtil.expire(counterKey, safePositive(rule.getWindowSeconds(), 60), TimeUnit.SECONDS);
        }
        long max = safePositive(rule.getMaxRequests(), 30);
        long ttl = queryExpireSeconds(counterKey, safePositive(rule.getWindowSeconds(), 60));
        return new LimitResult(count != null && count <= max, count != null ? count : 0L, ttl);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private LimitResult consumeByLua(RiskControlRule rule, String counterKey) {
        try {
            List result = stringRedisTemplate.execute(
                    LIMIT_SCRIPT,
                    Collections.singletonList(counterKey),
                    "1",
                    String.valueOf(safePositive(rule.getWindowSeconds(), 60)),
                    String.valueOf(safePositive(rule.getMaxRequests(), 30))
            );
            if (result == null || result.size() < 3) {
                return null;
            }
            long allowed = readLong(result.get(0));
            long count = readLong(result.get(1));
            long ttl = readLong(result.get(2));
            return new LimitResult(allowed == 1L, count, ttl);
        } catch (Exception e) {
            log.debug("[RiskControl] Lua限流降级，key={}, err={}", counterKey, e.getMessage());
            return null;
        }
    }

    private long increaseViolationCount(RiskControlRule rule, String subjectValue) {
        String violationKey = VIOLATION_KEY_PREFIX + rule.getRouteId() + ":" + rule.getSubjectType() + ":" + encodeValue(subjectValue);
        Long count = redisUtil.incr(violationKey, 1);
        if (count != null && count == 1L) {
            redisUtil.expire(violationKey, safePositive(rule.getViolationWindowSeconds(), 600), TimeUnit.SECONDS);
        }
        return count != null ? count : 0L;
    }

    private long queryExpireSeconds(String key, int fallback) {
        try {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            if (ttl != null && ttl > 0) {
                return ttl;
            }
        } catch (Exception e) {
            log.debug("[RiskControl] TTL查询失败 key={}, err={}", key, e.getMessage());
        }
        return fallback;
    }

    private void recordTotalMetric(String routeId) {
        incrementMetric(METRIC_GLOBAL_TOTAL);
        incrementRouteMetric(routeId, "total");
    }

    private void recordLimitedMetric(String routeId) {
        incrementMetric(METRIC_GLOBAL_LIMITED);
        incrementRouteMetric(routeId, "limited");
    }

    private void recordBlacklistMetric(String routeId) {
        incrementMetric(METRIC_GLOBAL_BLACKLIST);
        incrementRouteMetric(routeId, "blacklist");
    }

    private void incrementRouteMetric(String routeId, String metricType) {
        if (!StringUtils.hasText(routeId)) {
            return;
        }
        redisUtil.addToSet(METRIC_ROUTE_INDEX, routeId);
        incrementMetric(routeMetricKey(routeId, metricType));
    }

    private String routeMetricKey(String routeId, String metricType) {
        return METRIC_ROUTE_PREFIX + routeId + ":" + metricType;
    }

    private void incrementMetric(String key) {
        Long value = redisUtil.incr(key, 1);
        if (value != null && value == 1L) {
            redisUtil.expire(key, METRIC_EXPIRE_DAYS, TimeUnit.DAYS);
        }
    }

    private RiskControlRule buildRule(String routeId,
                                      String name,
                                      String method,
                                      String pathPattern,
                                      String subjectType,
                                      Integer windowSeconds,
                                      Integer maxRequests,
                                      Integer banThreshold,
                                      Integer banDurationSeconds,
                                      Integer violationWindowSeconds,
                                      String description) {
        RiskControlRule rule = new RiskControlRule();
        rule.setRouteId(routeId);
        rule.setName(name);
        rule.setMethod(method);
        rule.setPathPattern(pathPattern);
        rule.setSubjectType(subjectType);
        rule.setWindowSeconds(windowSeconds);
        rule.setMaxRequests(maxRequests);
        rule.setBanThreshold(banThreshold);
        rule.setBanDurationSeconds(banDurationSeconds);
        rule.setViolationWindowSeconds(violationWindowSeconds);
        rule.setDescription(description);
        rule.setEnabled(true);
        rule.setCustomized(false);
        rule.normalize();
        return rule;
    }

    private String normalizeMethod(String method) {
        return StringUtils.hasText(method) ? method.trim().toUpperCase() : "GET";
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        String value = path.trim();
        int idx = value.indexOf('?');
        return idx >= 0 ? value.substring(0, idx) : value;
    }

    private Long resolveUserId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object userId = request.getAttribute("userId");
        if (userId instanceof Long) {
            return (Long) userId;
        }
        if (userId != null) {
            try {
                return Long.valueOf(userId.toString());
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }

    private String resolveDeviceId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String header = StringUtils.hasText(properties.getDeviceIdHeader())
                ? properties.getDeviceIdHeader().trim()
                : "X-Device-Id";
        String deviceId = request.getHeader(header);
        if (!StringUtils.hasText(deviceId)) {
            return null;
        }
        return deviceId.trim();
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ip = null;
        if (properties.isTrustProxyHeaders()) {
            ip = request.getHeader("X-Forwarded-For");
            if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("X-Real-IP");
            }
        }
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (StringUtils.hasText(ip) && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return StringUtils.hasText(ip) ? ip : "unknown";
    }

    private String normalizeSubjectType(String subjectType) {
        String type = StringUtils.hasText(subjectType) ? subjectType.trim().toUpperCase() : SUBJECT_IP;
        if (!SUBJECT_IP.equals(type) && !SUBJECT_USER.equals(type) && !SUBJECT_DEVICE.equals(type)) {
            return SUBJECT_IP;
        }
        return type;
    }

    private String normalizeSubjectValue(String subjectValue) {
        if (!StringUtils.hasText(subjectValue)) {
            return null;
        }
        return subjectValue.trim();
    }

    private String blacklistKey(String subjectType, String subjectValue) {
        return BLACKLIST_KEY_PREFIX + subjectType + ":" + encodeValue(subjectValue);
    }

    private String blacklistIndexKey(String subjectType) {
        return BLACKLIST_INDEX_PREFIX + subjectType;
    }

    private String encodeValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeValue(String encoded) {
        if (!StringUtils.hasText(encoded)) {
            return null;
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private int parseInt(Object value, Integer fallback) {
        if (value == null) {
            return safePositive(fallback, 1);
        }
        try {
            int parsed = Integer.parseInt(value.toString());
            if (parsed <= 0) {
                return safePositive(fallback, 1);
            }
            return parsed;
        } catch (Exception e) {
            return safePositive(fallback, 1);
        }
    }

    private Boolean parseBoolean(Object value, Boolean fallback) {
        if (value == null) {
            return fallback == null ? Boolean.TRUE : fallback;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private int safePositive(Integer value, int fallback) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return value;
    }

    private double roundPercent(long part, long total) {
        if (total <= 0) {
            return 0D;
        }
        double value = (double) part * 100D / (double) total;
        return Math.round(value * 100D) / 100D;
    }

    private long readLong(Object value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return 0L;
        }
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private static class LimitResult {
        private final boolean allowed;
        private final long count;
        private final long retryAfterSeconds;

        private LimitResult(boolean allowed, long count, long retryAfterSeconds) {
            this.allowed = allowed;
            this.count = count;
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }
}
