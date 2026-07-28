package com.clouddisk.interceptor;

import com.clouddisk.common.ApiResponse;
import com.clouddisk.common.AuthenticationException;
import com.clouddisk.config.JwtProperties;
import com.clouddisk.utils.JwtTokenUtil;
import com.clouddisk.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

@Component
public class JwtAuthenticationInterceptor implements HandlerInterceptor {

    private static final Set<String> PUBLIC_PATHS = Set.of("/api/health", "/api/user/login");
    private static final Pattern PUBLIC_SHARE_INSPECTION =
            Pattern.compile("^/api/share/[^/]+$");
    private static final Pattern PUBLIC_SHARE_VERIFICATION =
            Pattern.compile("^/api/share/[^/]+/verify$");

    private final JwtTokenUtil jwtTokenUtil;
    private final JwtProperties jwtProperties;

    public JwtAuthenticationInterceptor(JwtTokenUtil jwtTokenUtil, JwtProperties jwtProperties) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        UserContext.clear();

        if (isPublicRequest(request)) {
            return true;
        }

        String authorization = request.getHeader(jwtProperties.getAuthorizationHeader());
        if (authorization == null || !authorization.startsWith(jwtProperties.getTokenPrefix())) {
            return reject("Missing or invalid Authorization header");
        }

        String token = authorization.substring(jwtProperties.getTokenPrefix().length()).trim();
        if (token.isEmpty()) {
            return reject("Missing or invalid Authorization header");
        }

        try {
            UserContext.setUserId(jwtTokenUtil.parseUserId(token));
            return true;
        } catch (AuthenticationException ex) {
            return reject(ex.getMessage());
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private boolean reject(String message) {
        UserContext.clear();
        throw new AuthenticationException(message);
    }

    private boolean isPublicRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (PUBLIC_PATHS.contains(requestUri)) {
            return true;
        }
        return ("GET".equals(request.getMethod())
                && PUBLIC_SHARE_INSPECTION.matcher(requestUri).matches())
                || ("POST".equals(request.getMethod())
                && PUBLIC_SHARE_VERIFICATION.matcher(requestUri).matches());
    }
}
