package com.claimsprocessing.claims.adapter.in.web;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class PaymentSignatureVerifier {

    public static final String SIGNATURE_HEADER = "X-Payment-Signature";

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final Mac mac;

    public PaymentSignatureVerifier(String secret) {
        byte[] key = Objects.requireNonNull(secret, "secret").getBytes(StandardCharsets.UTF_8);
        try {
            this.mac = Mac.getInstance(HMAC_SHA256);
            this.mac.init(new SecretKeySpec(key, HMAC_SHA256));
        } catch (GeneralSecurityException exception) {
            throw new MacUnavailableException(exception);
        }
    }

    public synchronized void verify(byte[] body, String signature) {
        Objects.requireNonNull(body, "body");
        if (signature == null) {
            throw new PaymentSignatureException();
        }
        byte[] expected = mac.doFinal(body);
        byte[] provided = decodeHex(signature);
        if (provided == null || !MessageDigest.isEqual(expected, provided)) {
            throw new PaymentSignatureException();
        }
    }

    private static byte[] decodeHex(String hex) {
        if (hex.length() % 2 != 0) {
            return null;
        }
        byte[] decoded = new byte[hex.length() / 2];
        for (int index = 0; index < hex.length(); index += 2) {
            int high = Character.digit(hex.charAt(index), 16);
            int low = Character.digit(hex.charAt(index + 1), 16);
            if (high < 0 || low < 0) {
                return null;
            }
            decoded[index / 2] = (byte) ((high << 4) + low);
        }
        return decoded;
    }

    private static final class MacUnavailableException extends RuntimeException {

        private MacUnavailableException(GeneralSecurityException cause) {
            super(HMAC_SHA256, cause);
        }
    }
}
