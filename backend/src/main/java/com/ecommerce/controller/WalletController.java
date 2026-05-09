package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Constants;
import com.ecommerce.common.Result;
import com.ecommerce.dto.RechargeDTO;
import com.ecommerce.entity.User;
import com.ecommerce.entity.WalletTransaction;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.mapper.WalletTransactionMapper;
import com.ecommerce.service.ModuleSwitchService;
import com.ecommerce.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    @Autowired
    private ModuleSwitchService moduleSwitchService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WalletTransactionMapper transactionMapper;

    @GetMapping("/balance")
    public Result<?> getBalance(HttpServletRequest request) {
        moduleSwitchService.requireEnabled("wallet");
        if (!moduleSwitchService.isEnabled("wallet")) {
            Map<String, Object> data = new HashMap<>();
            data.put("balance", BigDecimal.ZERO);
            return Result.success(data);
        }
        Long userId = (Long) request.getAttribute("userId");
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("balance", user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO);
        return Result.success(data);
    }

    @PostMapping("/recharge")
    @Transactional
    public Result<?> recharge(@Validated @RequestBody RechargeDTO dto, HttpServletRequest request) {
        moduleSwitchService.requireEnabled("wallet");
        if (!moduleSwitchService.isEnabled("wallet")) {
            return Result.error("钱包功能暂时关闭");
        }
        Long userId = (Long) request.getAttribute("userId");
        BigDecimal amount = dto.getAmount();

        if (amount.compareTo(new BigDecimal("50000")) > 0) {
            throw new BusinessException("单次充值不能超过50000元");
        }

        userMapper.addBalance(userId, amount);

        User afterUser = userService.getById(userId);
        if (afterUser == null) {
            throw new BusinessException("用户不存在");
        }

        WalletTransaction tx = new WalletTransaction();
        tx.setUserId(userId);
        tx.setType(Constants.WalletType.RECHARGE);
        tx.setAmount(amount);
        tx.setBalanceBefore(afterUser.getBalance().subtract(amount));
        tx.setBalanceAfter(afterUser.getBalance());
        tx.setDescription("余额充值 ¥" + amount.toPlainString());
        transactionMapper.insert(tx);

        Map<String, Object> data = new HashMap<>();
        data.put("balance", afterUser.getBalance());
        data.put("transaction", tx);
        return Result.success("充值成功", data);
    }

    @GetMapping("/transactions")
    public Result<?> transactions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String type,
            HttpServletRequest request) {
        moduleSwitchService.requireEnabled("wallet");
        if (!moduleSwitchService.isEnabled("wallet")) {
            Page<WalletTransaction> empty = new Page<>(page, size);
            empty.setRecords(java.util.Collections.emptyList());
            empty.setTotal(0);
            return Result.success(empty);
        }
        Long userId = (Long) request.getAttribute("userId");
        LambdaQueryWrapper<WalletTransaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WalletTransaction::getUserId, userId);
        if (type != null && !type.isEmpty()) {
            wrapper.eq(WalletTransaction::getType, type);
        }
        wrapper.orderByDesc(WalletTransaction::getCreateTime);
        IPage<WalletTransaction> result = transactionMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.success(result);
    }
}
