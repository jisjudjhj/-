package com.ecommerce.service.impl;

import com.ecommerce.entity.User;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);
    }

    @Test
    void resetPasswordShouldRotateTokenVersion() {
        User existing = new User();
        existing.setId(7L);
        existing.setPhone("13800000000");
        existing.setTokenVersion(2);

        when(userMapper.selectOne(any(), anyBoolean())).thenReturn(existing);
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-password");

        Long userId = userService.resetPassword("13800000000", "new-password");

        assertEquals(7L, userId);
        verify(userMapper).updateById(argThat(user ->
                user.getId().equals(7L)
                        && "encoded-password".equals(user.getPassword())
                        && Integer.valueOf(3).equals(user.getTokenVersion())));
    }
}
