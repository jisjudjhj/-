package com.ecommerce.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.entity.ImConversation;
import com.ecommerce.entity.ImMessage;
import com.ecommerce.entity.ImTicket;
import com.ecommerce.mapper.ImConversationMapper;
import com.ecommerce.mapper.ImMessageMapper;
import com.ecommerce.mapper.ImTicketMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class ImTicketSlaScheduler {

    private static final Logger log = LoggerFactory.getLogger(ImTicketSlaScheduler.class);

    private static final int PENDING_ASSIGN_TIMEOUT_MINUTES = 10;
    private static final int PROCESSING_TIMEOUT_MINUTES = 30;
    private static final int L1_ESCALATION_EXTENSION_MINUTES = 15;
    private static final int L2_ESCALATION_EXTENSION_MINUTES = 30;
    private static final int L1_COOLDOWN_MINUTES = 10;
    private static final int L2_COOLDOWN_MINUTES = 20;
    private static final int L3_COOLDOWN_MINUTES = 30;

    @Autowired
    private ImTicketMapper ticketMapper;

    @Autowired
    private ImConversationMapper conversationMapper;

    @Autowired
    private ImMessageMapper messageMapper;

    @Scheduled(fixedDelayString = "${im.ticket.sla.scan-interval-ms:60000}")
    public void scanTicketSla() {
        List<ImTicket> activeTickets = ticketMapper.selectList(new LambdaQueryWrapper<ImTicket>()
                .in(ImTicket::getTicketStatus, "pending_assign", "processing")
                .orderByAsc(ImTicket::getCreateTime));
        if (activeTickets == null || activeTickets.isEmpty()) {
            return;
        }
        for (ImTicket ticket : activeTickets) {
            try {
                processTicket(ticket);
            } catch (Exception exception) {
                log.warn("[ImTicketSla] SLA scan failed ticketId={}, error={}",
                        ticket == null ? null : ticket.getId(),
                        exception.getMessage());
            }
        }
    }

    private void processTicket(ImTicket ticket) {
        if (ticket == null || ticket.getId() == null) {
            return;
        }
        if (!"pending_assign".equals(ticket.getTicketStatus()) && !"processing".equals(ticket.getTicketStatus())) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int escalationLevel = safeInt(ticket.getSlaEscalationLevel());
        LocalDateTime expectedDeadline = resolveExpectedDeadline(ticket);
        if (expectedDeadline == null) {
            return;
        }

        if (escalationLevel <= 0) {
            LocalDateTime storedDeadline = ticket.getSlaDeadlineTime();
            if (storedDeadline == null || !storedDeadline.equals(expectedDeadline)) {
                ImTicket update = new ImTicket();
                update.setId(ticket.getId());
                update.setSlaDeadlineTime(expectedDeadline);
                if (ticket.getSlaEscalationLevel() == null) {
                    update.setSlaEscalationLevel(0);
                }
                ticketMapper.updateById(update);
                ticket.setSlaDeadlineTime(expectedDeadline);
            }
        }

        LocalDateTime deadline = ticket.getSlaDeadlineTime() != null ? ticket.getSlaDeadlineTime() : expectedDeadline;
        if (!now.isAfter(deadline)) {
            return;
        }
        if (!canEscalate(ticket, now)) {
            return;
        }

        long overtimeMinutes = Math.max(1L, Duration.between(deadline, now).toMinutes());
        int nextLevel = Math.min(3, safeInt(ticket.getSlaEscalationLevel()) + 1);
        LocalDateTime nextDeadline = now.plusMinutes(nextLevel <= 1
                ? L1_ESCALATION_EXTENSION_MINUTES
                : L2_ESCALATION_EXTENSION_MINUTES);

        ImTicket update = new ImTicket();
        update.setId(ticket.getId());
        update.setSlaEscalationLevel(nextLevel);
        update.setLastEscalationTime(now);
        update.setSlaDeadlineTime(nextDeadline);
        ticketMapper.updateById(update);

        escalateConversation(ticket.getConversationId());
        appendSystemMessage(ticket.getConversationId(), buildEscalationMessage(ticket, overtimeMinutes, nextLevel));

        log.info("[ImTicketSla] escalated ticketNo={}, level={}, overtime={}m",
                ticket.getTicketNo(), nextLevel, overtimeMinutes);
    }

    private LocalDateTime resolveExpectedDeadline(ImTicket ticket) {
        if (ticket == null || ticket.getCreateTime() == null) {
            return null;
        }
        if ("processing".equals(ticket.getTicketStatus())) {
            LocalDateTime base = ticket.getAssignedTime() != null ? ticket.getAssignedTime() : ticket.getCreateTime();
            return base.plusMinutes(PROCESSING_TIMEOUT_MINUTES);
        }
        return ticket.getCreateTime().plusMinutes(PENDING_ASSIGN_TIMEOUT_MINUTES);
    }

    private boolean canEscalate(ImTicket ticket, LocalDateTime now) {
        LocalDateTime lastEscalationTime = ticket.getLastEscalationTime();
        if (lastEscalationTime == null) {
            return true;
        }
        int level = safeInt(ticket.getSlaEscalationLevel());
        int cooldownMinutes;
        if (level <= 1) {
            cooldownMinutes = L1_COOLDOWN_MINUTES;
        } else if (level == 2) {
            cooldownMinutes = L2_COOLDOWN_MINUTES;
        } else {
            cooldownMinutes = L3_COOLDOWN_MINUTES;
        }
        return Duration.between(lastEscalationTime, now).toMinutes() >= cooldownMinutes;
    }

    private void escalateConversation(Long conversationId) {
        if (conversationId == null) {
            return;
        }
        ImConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            return;
        }
        ImConversation update = new ImConversation();
        update.setId(conversation.getId());
        update.setPriority("urgent");
        update.setIsEscalated(1);
        if (!"closed".equals(conversation.getStatus())) {
            update.setStatus("pending_support");
            update.setClosedTime(null);
        }
        update.setUnreadSupport(safeInt(conversation.getUnreadSupport()) + 1);
        conversationMapper.updateById(update);
    }

    private String buildEscalationMessage(ImTicket ticket, long overtimeMinutes, int level) {
        String ticketNo = ticket == null || ticket.getTicketNo() == null ? "--" : ticket.getTicketNo();
        return "SLA 超时预警：工单 " + ticketNo + " 已超时 " + overtimeMinutes
                + " 分钟，系统升级为 L" + level + "，请优先处理。";
    }

    private void appendSystemMessage(Long conversationId, String content) {
        if (conversationId == null || content == null || content.trim().isEmpty()) {
            return;
        }
        ImMessage message = new ImMessage();
        message.setConversationId(conversationId);
        message.setSenderRole("system");
        message.setSenderId(0L);
        message.setMessageType("system");
        message.setContent(content.trim());
        message.setIsSystem(1);
        messageMapper.insert(message);

        ImConversation latest = conversationMapper.selectById(conversationId);
        if (latest == null) {
            return;
        }
        ImConversation update = new ImConversation();
        update.setId(conversationId);
        update.setLastMessage(content.trim());
        update.setLastMessageType("system");
        update.setLastSenderRole("system");
        update.setLastSenderId(0L);
        update.setLastMessageTime(message.getCreateTime() == null ? LocalDateTime.now() : message.getCreateTime());
        update.setUnreadSupport(safeInt(latest.getUnreadSupport()) + 1);
        conversationMapper.updateById(update);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }
}
