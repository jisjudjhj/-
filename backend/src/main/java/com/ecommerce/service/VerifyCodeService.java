package com.ecommerce.service;

public interface VerifyCodeService {

    String generateAndSend(String target, String type, String clientIp);

    boolean verify(String target, String type, String code);
}
