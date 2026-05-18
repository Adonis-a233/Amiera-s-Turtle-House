package com.example.community.im.utils;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class AesUtil {

    public static String generateKey() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256, new SecureRandom());
        return Base64.getEncoder().encodeToString(kg.generateKey().getEncoded());
    }

    public static String encrypt(String plaintext, String base64Key) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        byte[] ivBytes = new byte[16];
        new SecureRandom().nextBytes(ivBytes);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(keyBytes, "AES"),
                new IvParameterSpec(ivBytes));

        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(ivBytes)
                + ":" + Base64.getEncoder().encodeToString(encrypted);
    }

    public static String decrypt(String ciphertext, String base64Key) throws Exception {
        String[] parts = ciphertext.split(":", 2);
        byte[] ivBytes = Base64.getDecoder().decode(parts[0]);
        byte[] encryptedBytes = Base64.getDecoder().decode(parts[1]);
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(keyBytes, "AES"),
                new IvParameterSpec(ivBytes));

        return new String(cipher.doFinal(encryptedBytes), StandardCharsets.UTF_8);
    }
}
