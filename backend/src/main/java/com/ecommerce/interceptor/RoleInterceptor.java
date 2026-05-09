package com.ecommerce.interceptor;

import com.alibaba.fastjson2.JSON;
import com.ecommerce.common.Constants;
import com.ecommerce.common.Result;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

@Component
public class RoleInterceptor implements HandlerInterceptor {

    private List<String> allowedRoles;

    public RoleInterceptor() {
    }

    public RoleInterceptor(String... roles) {
        this.allowedRoles = Arrays.asList(roles);
    }

    public void setAllowedRoles(List<String> allowedRoles) {
        this.allowedRoles = allowedRoles;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String role = (String) request.getAttribute("role");
        if (role == null || (allowedRoles != null && !allowedRoles.contains(role))) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(403);
            response.getWriter().write(JSON.toJSONString(Result.forbidden("无权访问，需要 " + allowedRoles + " 角色")));
            return false;
        }
        return true;
    }

    public static RoleInterceptor forRoles(String... roles) {
        RoleInterceptor interceptor = new RoleInterceptor();
        interceptor.setAllowedRoles(Arrays.asList(roles));
        return interceptor;
    }
}
