package com.planner.security;

import com.planner.common.BizException;
import com.planner.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements AsyncHandlerInterceptor {
    private final JwtUtil jwt;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) return true;
        String h = req.getHeader("Authorization");
        if (h == null || !h.startsWith("Bearer ")) throw new BizException(401, "未登录或登录已过期");
        Long uid = jwt.parse(h.substring(7));
        if (uid == null) throw new BizException(401, "未登录或登录已过期");
        UserContext.set(uid);
        return true;
    }

    /** SSE 等异步请求：原线程提前释放，避免 ThreadLocal 泄漏。 */
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        UserContext.clear();
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        UserContext.clear();
    }
}
