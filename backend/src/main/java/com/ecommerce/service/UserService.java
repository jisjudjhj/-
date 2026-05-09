package com.ecommerce.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ecommerce.entity.User;

import java.util.Map;

public interface UserService extends IService<User> {

    Map<String, Object> loginByPassword(String account, String password);

    Map<String, Object> loginByPhoneCode(String phone);

    User registerByPhone(String phone, String password, String nickname);

    User createMerchant(String phone, String password, String nickname, String email);

    Long resetPassword(String phone, String newPassword);

    IPage<User> getUserPage(int page, int size, String keyword, String role);

    boolean updateStatus(Long userId, Integer status);

    void submitProfileChange(Long userId, String newNickname, String newAvatar);

    Map<String, Object> getProfileChangeStatus(Long userId);
}
