package com.clouddisk.service;

public interface WechatHttpClient {

    WechatAuthService.WechatApiResponse exchangeCode(String appId, String appSecret, String code);
}
