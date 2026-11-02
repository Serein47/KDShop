package com.kdshop.payment;

import com.kdshop.common.BusinessException;
import com.kdshop.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** 按 key 字典序拼串后 HMAC-SHA256，见 ADR-0007。 */
@Component
public class PaymentSigner {

    private final String secret;

    public PaymentSigner(@Value("${kdshop.payment.secret}") String secret) {
        this.secret = secret;
    }

    public String sign(Map<String, String> params) {
        String content = canonical(params) + "&key=" + secret;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "签名失败");
        }
    }

    public void verify(Map<String, String> params) {
        String provided = params.get("sign");
        if (provided == null) {
            throw new BusinessException(ErrorCode.SIGNATURE_INVALID);
        }
        String expected = sign(params);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.SIGNATURE_INVALID);
        }
    }

    private String canonical(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .filter(entry -> !"sign".equals(entry.getKey()))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }
}
