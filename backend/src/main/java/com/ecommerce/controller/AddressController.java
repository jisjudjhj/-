package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Result;
import com.ecommerce.entity.Address;
import com.ecommerce.mapper.AddressMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/user/addresses")
public class AddressController {

    @Autowired
    private AddressMapper addressMapper;

    @GetMapping
    public Result<?> list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<Address> list = addressMapper.selectList(
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getUserId, userId)
                        .orderByDesc(Address::getIsDefault)
                        .orderByDesc(Address::getUpdateTime));
        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Address addr = addressMapper.selectById(id);
        if (addr == null || !addr.getUserId().equals(userId)) {
            throw new BusinessException("地址不存在");
        }
        return Result.success(addr);
    }

    @PostMapping
    public Result<?> create(@RequestBody Address address, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        address.setUserId(userId);
        address.setId(null);

        long count = addressMapper.selectCount(
                new LambdaQueryWrapper<Address>().eq(Address::getUserId, userId));
        if (count >= 20) {
            throw new BusinessException("最多保存20个收货地址");
        }

        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            addressMapper.clearDefault(userId);
        }
        if (count == 0) {
            address.setIsDefault(1);
        }

        addressMapper.insert(address);
        return Result.success("添加成功", address);
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody Address address,
                            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Address existing = addressMapper.selectById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BusinessException("地址不存在");
        }
        address.setId(id);
        address.setUserId(userId);

        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            addressMapper.clearDefault(userId);
        }

        addressMapper.updateById(address);
        return Result.success("更新成功");
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Address existing = addressMapper.selectById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BusinessException("地址不存在");
        }
        addressMapper.deleteById(id);

        if (existing.getIsDefault() != null && existing.getIsDefault() == 1) {
            List<Address> remaining = addressMapper.selectList(
                    new LambdaQueryWrapper<Address>()
                            .eq(Address::getUserId, userId)
                            .orderByDesc(Address::getUpdateTime)
                            .last("LIMIT 1"));
            if (!remaining.isEmpty()) {
                Address first = remaining.get(0);
                first.setIsDefault(1);
                addressMapper.updateById(first);
            }
        }
        return Result.success("删除成功");
    }

    @GetMapping("/default")
    public Result<?> getDefault(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<Address> list = addressMapper.selectList(
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getUserId, userId)
                        .eq(Address::getIsDefault, 1)
                        .last("LIMIT 1"));
        if (list.isEmpty()) {
            list = addressMapper.selectList(
                    new LambdaQueryWrapper<Address>()
                            .eq(Address::getUserId, userId)
                            .orderByDesc(Address::getUpdateTime)
                            .last("LIMIT 1"));
        }
        return Result.success(list.isEmpty() ? null : list.get(0));
    }

    @PutMapping("/{id}/default")
    public Result<?> setDefault(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Address addr = addressMapper.selectById(id);
        if (addr == null || !addr.getUserId().equals(userId)) {
            throw new BusinessException("地址不存在");
        }
        addressMapper.clearDefault(userId);
        addr.setIsDefault(1);
        addressMapper.updateById(addr);
        return Result.success("设置默认地址成功");
    }
}
