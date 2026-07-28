package com.clouddisk.service;

import com.clouddisk.config.WechatProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WechatAuthService {

    private static final Logger log = LoggerFactory.getLogger(WechatAuthService.class);

    private final WechatHttpClient wechatHttpClient;
    private final WechatProperties wechatProperties;

    public WechatAuthService(WechatHttpClient wechatHttpClient, WechatProperties wechatProperties) {
        this.wechatHttpClient = wechatHttpClient;
        this.wechatProperties = wechatProperties;
    }

    public WechatSession exchangeCode(String code) {
        if (code == null || code.isBlank()) {
            throw WechatLoginException.badRequest("code must not be blank");
        }

        WechatApiResponse response;
        try {
            response = wechatHttpClient.exchangeCode(
                    wechatProperties.getAppId(),
                    wechatProperties.getAppSecret(),
                    code.trim()
            );
        } catch (RuntimeException ex) {
            log.error("WeChat code2session request failed", ex);
            throw WechatLoginException.badGateway("WeChat login failed");
        }

        if (response == null) {
            throw WechatLoginException.badGateway("WeChat login failed");
        }

        if (response.errcode() != null && response.errcode() != 0) {
            log.warn("WeChat upstream login failed: errcode={}, errmsg={}", response.errcode(), response.errmsg());
            throw WechatLoginException.badGateway("WeChat login failed");
        }

        if (response.openid() == null || response.openid().isBlank()) {
            throw WechatLoginException.badGateway("WeChat login failed");
        }

        return new WechatSession(response.openid().trim());
    }

    public record WechatSession(String openid) {
    }

    public record WechatApiResponse(String openid, String session_key, Integer errcode, String errmsg) {
    }
}
