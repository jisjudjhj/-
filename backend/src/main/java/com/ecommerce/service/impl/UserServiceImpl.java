package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.Constants;
import com.ecommerce.entity.ProfileChangeRequest;
import com.ecommerce.entity.User;
import com.ecommerce.mapper.ProfileChangeRequestMapper;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.service.ManagementWorkbenchRealtimeService;
import com.ecommerce.service.UserService;
import com.ecommerce.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public Map<String, Object> loginByPassword(String account, String password) {
        if (!StringUtils.hasText(account) || !StringUtils.hasText(password)) {
            throw new BusinessException(400, "账号和密码不能为空");
        }

        User user = this.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, account)
                .or().eq(User::getPhone, account));
        if (user == null) {
            throw new BusinessException("账号不存在");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("密码错误");
        }
        checkUserStatus(user);
        return buildLoginResult(user);
    }

    @Override
    public Map<String, Object> loginByPhoneCode(String phone) {
        if (!StringUtils.hasText(phone)) {
            throw new BusinessException(400, "手机号不能为空");
        }
        User user = this.getOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user == null) {
            throw new BusinessException("该手机号未注册");
        }
        checkUserStatus(user);
        return buildLoginResult(user);
    }

    @Override
    public User registerByPhone(String phone, String password, String nickname) {
        return createUser(phone, password, nickname, null, Constants.Role.USER);
    }

    @Override
    public User createMerchant(String phone, String password, String nickname, String email) {
        return createUser(phone, password, nickname, email, Constants.Role.MERCHANT);
    }

    @Override
    public Long resetPassword(String phone, String newPassword) {
        User user = this.getOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user == null) {
            throw new BusinessException("该手机号未注册");
        }
        User update = new User();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(newPassword));
        update.setTokenVersion((user.getTokenVersion() != null ? user.getTokenVersion() : 0) + 1);
        this.updateById(update);
        return user.getId();
    }

    @Override
    public IPage<User> getUserPage(int page, int size, String keyword, String role) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getPhone, keyword)
                    .or().like(User::getNickname, keyword)
                    .or().like(User::getEmail, keyword));
        }
        if (StringUtils.hasText(role)) {
            wrapper.eq(User::getRole, role);
        }
        wrapper.orderByDesc(User::getCreateTime);
        return this.page(new Page<>(page, size), wrapper);
    }

    @Override
    public boolean updateStatus(Long userId, Integer status) {
        User user = new User();
        user.setId(userId);
        user.setStatus(status);
        return this.updateById(user);
    }

    @Autowired
    private ProfileChangeRequestMapper profileChangeRequestMapper;

    @Autowired
    private ManagementWorkbenchRealtimeService managementWorkbenchRealtimeService;

    @Override
    public void submitProfileChange(Long userId, String newNickname, String newAvatar) {
        User currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException("用户不存在");
        }
        if (!StringUtils.hasText(newNickname) && !StringUtils.hasText(newAvatar)) {
            throw new BusinessException("请至少修改一项内容");
        }
        if (currentUser.getLastProfileChange() != null
                && currentUser.getLastProfileChange().plusDays(7).isAfter(LocalDateTime.now())) {
            throw new BusinessException("资料修改间隔需满7天，请稍后再试");
        }
        long pendingCount = profileChangeRequestMapper.selectCount(
                new LambdaQueryWrapper<ProfileChangeRequest>()
                        .eq(ProfileChangeRequest::getUserId, userId)
                        .eq(ProfileChangeRequest::getStatus, 0));
        if (pendingCount > 0) {
            throw new BusinessException("您已有待审核的修改申请，请等待审核结果");
        }
        ProfileChangeRequest req = new ProfileChangeRequest();
        req.setUserId(userId);
        req.setOldNickname(currentUser.getNickname());
        req.setOldAvatar(currentUser.getAvatar());
        req.setNewNickname(StringUtils.hasText(newNickname) ? newNickname : null);
        req.setNewAvatar(StringUtils.hasText(newAvatar) ? newAvatar : null);
        req.setStatus(0);
        profileChangeRequestMapper.insert(req);

        Map<String, Object> payload = new HashMap<>();
        payload.put("scope", "profile-change");
        payload.put("userId", userId);
        payload.put("requestId", req.getId());
        managementWorkbenchRealtimeService.notifyAdmins("profile-change-submitted", payload);
    }

    @Override
    public Map<String, Object> getProfileChangeStatus(Long userId) {
        User currentUser = this.getById(userId);
        ProfileChangeRequest latest = profileChangeRequestMapper.selectOne(
                new LambdaQueryWrapper<ProfileChangeRequest>()
                        .eq(ProfileChangeRequest::getUserId, userId)
                        .orderByDesc(ProfileChangeRequest::getCreateTime)
                        .last("LIMIT 1"));

        Map<String, Object> data = new HashMap<>();
        boolean canModify = true;
        String reason = null;

        if (currentUser != null && currentUser.getLastProfileChange() != null
                && currentUser.getLastProfileChange().plusDays(7).isAfter(LocalDateTime.now())) {
            canModify = false;
            reason = "距离上次修改不足7天";
        }
        if (latest != null && latest.getStatus() == 0) {
            canModify = false;
            reason = "有待审核的申请";
        }

        data.put("canModify", canModify);
        data.put("reason", reason);
        data.put("lastChange", currentUser != null ? currentUser.getLastProfileChange() : null);
        data.put("latestRequest", latest);
        return data;
    }

    private User createUser(String phone, String password, String nickname, String email, String role) {
        long phoneCount = this.count(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (phoneCount > 0) {
            throw new BusinessException("该手机号已注册");
        }
        if (Constants.Role.ADMIN.equals(role)) {
            long adminCount = this.count(new LambdaQueryWrapper<User>().eq(User::getRole, Constants.Role.ADMIN));
            if (adminCount > 0) {
                throw new BusinessException("系统管理员账号已存在");
            }
        }

        User user = new User();
        user.setUsername(phone);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(StringUtils.hasText(nickname) ? nickname.trim() : buildDefaultNickname(role, phone));
        user.setEmail(StringUtils.hasText(email) ? email.trim() : null);
        user.setRole(role);
        user.setStatus(1);
        user.setBalance(BigDecimal.ZERO);
        user.setEmailVerified(0);
        user.setAvatar("https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png");
        this.save(user);
        return user;
    }

    private void checkUserStatus(User user) {
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }
    }

    private String buildDefaultNickname(String role, String phone) {
        String suffix = phone.length() >= 4 ? phone.substring(phone.length() - 4) : phone;
        if (Constants.Role.MERCHANT.equals(role)) {
            return "商家" + suffix;
        }
        if (Constants.Role.ADMIN.equals(role)) {
            return "管理员" + suffix;
        }
        return "用户" + suffix;
    }

    private Map<String, Object> buildLoginResult(User user) {
        int tokenVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole(), tokenVersion);
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        user.setPassword(null);
        result.put("user", user);
        return result;
    }
}
