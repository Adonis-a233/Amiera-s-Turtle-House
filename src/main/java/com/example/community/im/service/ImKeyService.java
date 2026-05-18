package com.example.community.im.service;

import com.example.community.im.utils.AesUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 信封加密服务：用 KEK（Key Encryption Key）保护会话 DEK。
 *
 * KEK 存于环境变量，不进数据库；即使数据库全量泄露，攻击者也无法还原 DEK。
 * Redis 缓存存明文 DEK（内部服务，不对外暴露），数据库存 KEK 加密后的 DEK。
 */
@Service
public class ImKeyService {

    @Value("${im.kek}")
    private String kek;

    public String encryptDek(String plainDek) throws Exception {
        return AesUtil.encrypt(plainDek, kek);
    }

    public String decryptDek(String encryptedDek) throws Exception {
        return AesUtil.decrypt(encryptedDek, kek);
    }
}
