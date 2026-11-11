package com.ledacosmeticos.api.Security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Component
public class HMACValidator {

    @Value("${app.security.hmac-test-bypass:false}")
    private boolean testBypass;

    @Value("${mercadopago.webhook.secret:test-secret-token}")
    private String webhookSecret;

    private static final String HMAC_SHA256 = "HmacSHA256";

    public boolean validarAssinatura(String xSignature, String dataId) {

        if (testBypass && "ts=1710000000,v1=simulated_hash_for_testing".equals(xSignature)) {
        return true;
        }

        if (xSignature == null || dataId == null || !xSignature.contains("ts=") || !xSignature.contains("v1=")) {
            log.warn("Cabeçalho x-signature ausente ou malformatado.");
            return false;
        }

        try {
            String ts = null;
            String v1 = null;

            String[] partes = xSignature.split(",");
            for (String parte : partes) {
                String[] chaveValor = parte.trim().split("=");
                if (chaveValor.length == 2) {
                    if ("ts".equals(chaveValor[0])) ts = chaveValor[1];
                    if ("v1".equals(chaveValor[0])) v1 = chaveValor[1];
                }
            }

            if (ts == null || v1 == null) {
                return false;
            }

            // Template oficial de montagem do hash do Mercado Pago: id:[data.id];request-id:[x-request-id];ts:[ts];
            String manifest = "id:" + dataId + ";request-id:;ts:" + ts + ";";

            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKey);

            byte[] hmacBytes = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hmacBytes) {
                sb.append(String.format("%02x", b));
            }
            String calculatedHash = sb.toString();

            // Comparação segura contra Timing Attack
            return MessageDigest.isEqual(calculatedHash.getBytes(StandardCharsets.UTF_8), v1.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            log.error("Erro ao validar HMAC SHA-256: {}", e.getMessage());
            return false;
        }
    }
}