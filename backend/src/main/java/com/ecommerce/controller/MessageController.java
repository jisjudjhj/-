package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Result;
import com.ecommerce.entity.Message;
import com.ecommerce.mapper.MessageMapper;
import com.ecommerce.service.ManagementWorkbenchRealtimeService;
import com.ecommerce.service.ModuleSwitchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    @Autowired
    private ModuleSwitchService moduleSwitchService;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private ManagementWorkbenchRealtimeService managementWorkbenchRealtimeService;

    @GetMapping
    public Result<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            HttpServletRequest request) {
        moduleSwitchService.requireEnabled("message");
        if (!moduleSwitchService.isEnabled("message")) {
            return Result.success(emptyPage(page, size));
        }
        Long userId = (Long) request.getAttribute("userId");

        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getUserId, userId)
               .orderByDesc(Message::getCreateTime);
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Message::getType, type);
        }

        IPage<Message> msgPage = messageMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.success(msgPage);
    }

    @GetMapping("/unread-count")
    public Result<?> unreadCount(HttpServletRequest request) {
        if (!moduleSwitchService.isEnabled("message")) {
            Map<String, Object> result = new HashMap<>();
            result.put("count", 0);
            return Result.success(result);
        }
        Long userId = (Long) request.getAttribute("userId");
        int count = messageMapper.selectUnreadCount(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        return Result.success(result);
    }

    @PutMapping("/{id}/read")
    public Result<?> markRead(@PathVariable Long id, HttpServletRequest request) {
        moduleSwitchService.requireEnabled("message");
        if (!moduleSwitchService.isEnabled("message")) {
            return Result.success();
        }
        Long userId = (Long) request.getAttribute("userId");
        Message msg = messageMapper.selectById(id);
        if (msg == null || !msg.getUserId().equals(userId)) {
            throw new BusinessException("消息不存在");
        }
        msg.setIsRead(1);
        messageMapper.updateById(msg);
        managementWorkbenchRealtimeService.notifyUserMessageChanged(userId, "user-message-read");
        managementWorkbenchRealtimeService.notifyMerchantMessageChanged(
                userId,
                "merchant-message-read",
                Collections.singletonMap("scope", "message")
        );
        return Result.success("已读");
    }

    @PutMapping("/read-all")
    public Result<?> markAllRead(HttpServletRequest request) {
        moduleSwitchService.requireEnabled("message");
        if (!moduleSwitchService.isEnabled("message")) {
            return Result.success();
        }
        Long userId = (Long) request.getAttribute("userId");
        Message update = new Message();
        update.setIsRead(1);
        messageMapper.update(update,
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getUserId, userId)
                        .eq(Message::getIsRead, 0));
        managementWorkbenchRealtimeService.notifyUserMessageChanged(userId, "user-message-read-all");
        managementWorkbenchRealtimeService.notifyMerchantMessageChanged(
                userId,
                "merchant-message-read-all",
                Collections.singletonMap("scope", "message")
        );
        return Result.success("全部已读");
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, HttpServletRequest request) {
        moduleSwitchService.requireEnabled("message");
        if (!moduleSwitchService.isEnabled("message")) {
            return Result.success();
        }
        Long userId = (Long) request.getAttribute("userId");
        Message msg = messageMapper.selectById(id);
        if (msg == null || !msg.getUserId().equals(userId)) {
            throw new BusinessException("消息不存在");
        }
        messageMapper.deleteById(id);
        managementWorkbenchRealtimeService.notifyUserMessageChanged(userId, "user-message-deleted");
        managementWorkbenchRealtimeService.notifyMerchantMessageChanged(
                userId,
                "merchant-message-deleted",
                Collections.singletonMap("scope", "message")
        );
        return Result.success("删除成功");
    }

    private <T> IPage<T> emptyPage(int page, int size) {
        Page<T> empty = new Page<>(page, size);
        empty.setRecords(Collections.emptyList());
        empty.setTotal(0);
        return empty;
    }
}
