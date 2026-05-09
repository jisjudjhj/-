package com.ecommerce.utils;

import com.alibaba.fastjson2.JSONObject;
import com.ecommerce.entity.RefundRequest;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class RefundViewUtil {

    private static final String INTERVENTION_PREFIX = "#SYS_INTERVENTION#";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private RefundViewUtil() {
    }

    public static void enrichRefundView(RefundRequest refund) {
        if (refund == null) {
            return;
        }
        InterventionMeta meta = extractInterventionMeta(refund.getDescription());
        refund.setDescription(stripSystemMetadata(refund.getDescription()));
        if (meta != null) {
            refund.setInterventionStatus(meta.status);
            refund.setInterventionReason(meta.reason);
            refund.setInterventionTime(meta.time);
        }
    }

    public static boolean hasPendingIntervention(String description) {
        InterventionMeta meta = extractInterventionMeta(description);
        return meta != null && "pending".equals(meta.status);
    }

    public static String upsertInterventionMetadata(String description, String status, String reason, LocalDateTime time) {
        List<String> lines = splitDescriptionLines(description);
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            if (!line.startsWith(INTERVENTION_PREFIX)) {
                kept.add(line);
            }
        }
        JSONObject payload = new JSONObject();
        payload.put("status", status);
        payload.put("reason", reason);
        payload.put("time", time == null ? null : FORMATTER.format(time));
        kept.add(INTERVENTION_PREFIX + payload.toJSONString());
        return String.join("\n", kept).trim();
    }

    public static InterventionMeta extractInterventionMeta(String description) {
        if (!StringUtils.hasText(description)) {
            return null;
        }
        for (String line : splitDescriptionLines(description)) {
            if (!line.startsWith(INTERVENTION_PREFIX)) {
                continue;
            }
            String payload = line.substring(INTERVENTION_PREFIX.length()).trim();
            if (!StringUtils.hasText(payload)) {
                continue;
            }
            try {
                JSONObject jsonObject = JSONObject.parseObject(payload);
                InterventionMeta meta = new InterventionMeta();
                meta.status = jsonObject.getString("status");
                meta.reason = jsonObject.getString("reason");
                meta.time = jsonObject.getString("time");
                return meta;
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    public static String stripSystemMetadata(String description) {
        if (!StringUtils.hasText(description)) {
            return description;
        }
        List<String> kept = new ArrayList<>();
        for (String line : splitDescriptionLines(description)) {
            if (!line.startsWith(INTERVENTION_PREFIX)) {
                kept.add(line);
            }
        }
        return String.join("\n", kept).trim();
    }

    private static List<String> splitDescriptionLines(String description) {
        List<String> lines = new ArrayList<>();
        if (!StringUtils.hasText(description)) {
            return lines;
        }
        String normalized = description.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : normalized.split("\n")) {
            if (line != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    public static class InterventionMeta {
        public String status;
        public String reason;
        public String time;
    }
}
