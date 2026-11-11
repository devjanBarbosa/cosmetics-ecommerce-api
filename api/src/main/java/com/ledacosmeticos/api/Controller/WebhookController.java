package com.ledacosmeticos.api.Controller;

import com.ledacosmeticos.api.DTO.MercadoPagoWebhookDTO;
import com.ledacosmeticos.api.Security.HMACValidator;
import com.ledacosmeticos.api.Service.PedidoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final PedidoService pedidoService;
    private final HMACValidator hmacValidator;

    @PostMapping("/mercadopago")
    public ResponseEntity<Void> receberNotificacao(
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "id", required = false) String paramId,
            @RequestBody(required = false) MercadoPagoWebhookDTO payload) {

        String paymentId = null;

        if (payload != null && "payment".equalsIgnoreCase(payload.type()) && payload.data() != null) {
            paymentId = payload.data().id();
        } else if ("payment".equalsIgnoreCase(topic) && paramId != null) {
            paymentId = paramId;
        }

        if (paymentId == null) {
            return ResponseEntity.ok().build();
        }

        // RF02: Validação Criptográfica de Assinatura (Fail-Fast na borda)
        if (!hmacValidator.validarAssinatura(xSignature, paymentId)) {
            log.warn("Assinatura HMAC inválida para notificação de pagamento ID: {}", paymentId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build(); // 401 Unauthorized
        }

        try {
            pedidoService.processarNotificacaoPagamento(paymentId);
            return ResponseEntity.ok().build(); // 200 OK
        } catch (Exception e) {
            log.error("Erro interno ao processar webhook: {}", e.getMessage());
            return ResponseEntity.internalServerError().build(); // 500 para retry do gateway
        }
    }
}