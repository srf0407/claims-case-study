package com.claimsprocessing.claims.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class PaymentSignatureVerifierTest {

    private static final String SECRET = "test-secret";
    private static final byte[] BODY = "{\"claimId\":\"abc\"}".getBytes(StandardCharsets.UTF_8);

    private final PaymentSignatureVerifier verifier = new PaymentSignatureVerifier(SECRET);

    @Test
    void should_accept_a_valid_signature() throws Exception {
        // given
        String signature = sign(BODY, SECRET);

        // when / then
        assertThatCode(() -> verifier.verify(BODY, signature)).doesNotThrowAnyException();
    }

    @Test
    void should_reject_a_wrong_signature() throws Exception {
        // given
        String signature = sign(BODY, "other-secret");

        // when / then
        assertThatThrownBy(() -> verifier.verify(BODY, signature))
                .isInstanceOf(PaymentSignatureException.class);
    }

    @Test
    void should_reject_a_missing_signature_header() {
        // given / when / then
        assertThatThrownBy(() -> verifier.verify(BODY, null))
                .isInstanceOf(PaymentSignatureException.class);
    }

    @Test
    void should_reject_a_signature_that_is_not_hex() {
        // given / when / then
        assertThatThrownBy(() -> verifier.verify(BODY, "zz"))
                .isInstanceOf(PaymentSignatureException.class);
    }

    @Test
    void should_reject_a_body_changed_after_signing() throws Exception {
        // given
        String signature = sign(BODY, SECRET);
        byte[] changed = "{\"claimId\":\"xyz\"}".getBytes(StandardCharsets.UTF_8);

        // when / then
        assertThatThrownBy(() -> verifier.verify(changed, signature))
                .isInstanceOf(PaymentSignatureException.class);
    }

    private static String sign(byte[] body, String secret) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signed = mac.doFinal(body);
        StringBuilder hex = new StringBuilder(signed.length * 2);
        for (byte value : signed) {
            hex.append("%02x".formatted(value & 0xff));
        }
        return hex.toString();
    }
}
