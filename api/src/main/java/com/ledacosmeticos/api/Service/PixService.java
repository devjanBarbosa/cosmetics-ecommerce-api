package com.ledacosmeticos.api.Service;

import com.ledacosmeticos.api.Model.Cliente;
import com.ledacosmeticos.api.Model.Pedido;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
import com.mercadopago.core.MPRequestOptions; // confira o import na versão do seu SDK
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
public class PixService {

    private static final int VALIDADE_PIX_MINUTOS = 10;

    @Value("${mercadopago.access_token}")
    private String mercadoPagoAccessToken;

    @PostConstruct
    public void init() {
        MercadoPagoConfig.setAccessToken(mercadoPagoAccessToken);
    }

    public Payment criarCobrancaPix(Pedido pedido) throws MPException, MPApiException {
        Cliente cliente = pedido.getCliente();

        String email = (cliente.getEmail() != null && !cliente.getEmail().isBlank())
                ? cliente.getEmail()
                : "cliente+" + cliente.getWhatsapp() + "@ledacosmeticos.com";

        // Valor em BigDecimal com 2 casas, sem passar pelo construtor BigDecimal(double)
        BigDecimal valor = BigDecimal.valueOf(pedido.getValorTotal()).setScale(2, RoundingMode.HALF_UP);

        PaymentCreateRequest request = PaymentCreateRequest.builder()
                .transactionAmount(valor)
                .description("Pedido #" + pedido.getId().toString().substring(0, 8) + " - Leda Cosméticos")
                .paymentMethodId("pix")
                .dateOfExpiration(OffsetDateTime.now(ZoneOffset.of("-03:00")).plusMinutes(VALIDADE_PIX_MINUTOS))
                // CORREÇÃO: é por aqui que o webhook acha o pedido depois
                .externalReference(pedido.getId().toString())
                .payer(PaymentPayerRequest.builder()
                        .email(email)
                        .firstName(cliente.getNome().split(" ")[0])
                        .build())
                .build();

        // Chave de idempotência: um retry com o mesmo pedido não gera cobrança duplicada
        MPRequestOptions options = MPRequestOptions.builder()
                .customHeaders(Map.of("x-idempotency-key", pedido.getId().toString()))
                .build();

        return new PaymentClient().create(request, options);
    }

    public Payment buscarPagamentoPorId(String paymentId) throws MPException, MPApiException {
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("O ID do pagamento não pode ser nulo ou vazio.");
        }
        try {
            return new PaymentClient().get(Long.parseLong(paymentId.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("O ID do pagamento fornecido não é um número válido.", e);
        }
    }
}