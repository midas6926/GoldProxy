package com.midas.goldproxy.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class HmacUtil {
    private static final String HMAC_ALGO = "HmacSHA256";

    private HmacUtil() {}

    public static String computeHmacBase64(String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] raw = mac.doFinal(data);
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC", e);
        }
    }

    // Constant-time comparison to avoid timing attacks
    public static boolean verifyHmacBase64(String secret, byte[] data, String expectedBase64) {
        if (secret == null || expectedBase64 == null) return false;
        String actual = computeHmacBase64(secret, data);
        return constantTimeEquals(actual, expectedBase64);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aa = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (aa.length != bb.length) return false;
        int result = 0;
        for (int i = 0; i < aa.length; i++) result |= aa[i] ^ bb[i];
        return result == 0;
    }
}
