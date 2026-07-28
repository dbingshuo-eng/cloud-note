package com.clouddisk.filter;

import com.clouddisk.common.ApiResponse;
import com.clouddisk.common.AuthenticationException;
import com.clouddisk.config.JwtProperties;
import com.clouddisk.utils.JwtTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UploadAuthenticationFilter extends OncePerRequestFilter {

    private static final String UPLOAD_PATH = "/api/file/upload";

    private final JwtTokenUtil jwtTokenUtil;
    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;

    public UploadAuthenticationFilter(
            JwtTokenUtil jwtTokenUtil,
            JwtProperties jwtProperties,
            ObjectMapper objectMapper
    ) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.jwtProperties = jwtProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return !UPLOAD_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(jwtProperties.getAuthorizationHeader());
        String tokenPrefix = jwtProperties.getTokenPrefix();
        if (authorization == null || !authorization.startsWith(tokenPrefix)) {
            writeUnauthorized(response, "Missing or invalid Authorization header");
            return;
        }

        String token = authorization.substring(tokenPrefix.length()).trim();
        if (token.isEmpty()) {
            writeUnauthorized(response, "Missing or invalid Authorization header");
            return;
        }

        try {
            jwtTokenUtil.parseUserId(token);
        } catch (AuthenticationException exception) {
            writeUnauthorized(response, exception.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(401, message));
    }
}
