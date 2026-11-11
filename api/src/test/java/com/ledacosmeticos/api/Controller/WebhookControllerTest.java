package com.ledacosmeticos.api.Controller;

import com.ledacosmeticos.api.DTO.MercadoPagoWebhookDTO;
import com.ledacosmeticos.api.Security.HMACValidator;
import com.ledacosmeticos.api.Service.PedidoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    @Mock
    private PedidoService pedidoService;

    @Mock
    private HMACValidator hmacValidator;

    @InjectMocks
    private WebhookController webhookController;

    @Test
    @DisplayName("Deve retornar 401 Unauthorized quando o HMAC for inválido")
    void deveRetornar401QuandoAssinaturaInvalida() {
        String xSignature = "invalid_signature";
        MercadoPagoWebhookDTO payload = new MercadoPagoWebhookDTO(
            "payment.updated", "payment", new MercadoPagoWebhookDTO.DataDTO("12345")
        );

        when(hmacValidator.validarAssinatura(xSignature, "12345")).thenReturn(false);

        ResponseEntity<Void> response = webhookController.receberNotificacao(xSignature, null, null, payload);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(pedidoService, never()).processarNotificacaoPagamento(anyString());
    }

    @Test
    @DisplayName("Deve processar notificação e retornar 200 OK quando o HMAC for válido")
    void deveRetornar200QuandoAssinaturaValida() {
        String xSignature = "valid_signature";
        MercadoPagoWebhookDTO payload = new MercadoPagoWebhookDTO(
            "payment.updated", "payment", new MercadoPagoWebhookDTO.DataDTO("12345")
        );

        when(hmacValidator.validarAssinatura(xSignature, "12345")).thenReturn(true);

        ResponseEntity<Void> response = webhookController.receberNotificacao(xSignature, null, null, payload);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(pedidoService, times(1)).processarNotificacaoPagamento("12345");
    }
}