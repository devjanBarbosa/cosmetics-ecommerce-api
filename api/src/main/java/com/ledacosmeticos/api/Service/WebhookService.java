package com.ledacosmeticos.api.Service;

import com.ledacosmeticos.api.DTO.MercadoPagoWebhookDTO;
import com.ledacosmeticos.api.Model.Pedido;
import com.ledacosmeticos.api.Model.StatusPedido; // Ajuste para o seu enum de status
import com.ledacosmeticos.api.Repository.PedidoRepository;
import com.mercadopago.resources.payment.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final PixService pixService;
    private final PedidoRepository pedidoRepository;

    @Transactional
    public void processarNotificacao(MercadoPagoWebhookDTO payload) {
        // 1. Filtrar apenas eventos relevantes de pagamento
        if (payload == null || payload.data() == null || payload.data().id() == null) {
            return;
        }

        String paymentId = payload.data().id();

        try {
            // 2. Busca o pagamento oficial no Mercado Pago
            Payment payment = pixService.buscarPagamentoPorId(paymentId);

            if (payment == null || !"approved".equalsIgnoreCase(payment.getStatus())) {
                log.info("Pagamento {} ainda não aprovado (status: {})", paymentId, payment != null ? payment.getStatus() : "null");
                return;
            }

            // 3. Recupera o ID do Pedido gravado no externalReference
            String pedidoIdStr = payment.getExternalReference();
            if (pedidoIdStr == null || pedidoIdStr.isBlank()) {
                log.warn("Pagamento {} aprovado sem external_reference associado.", paymentId);
                return;
            }

            UUID pedidoId = UUID.fromString(pedidoIdStr);
            Pedido pedido = pedidoRepository.findById(pedidoId)
                    .orElseThrow(() -> new RuntimeException("Pedido não encontrado com ID: " + pedidoId));

            // 4. BARREIRA DE IDEMPOTÊNCIA (Fail-Fast):
            // Se já foi processado por outra requisição concorrente, para aqui
            if (pedido.getStatus() == StatusPedido.EM_PREPARACAO) {
                log.info("Pedido {} já estava com status EM_PREPARACAO. Ignorando reprocessamento.", pedidoId);
                return;
            }

            // 5. Atualiza o status e confirma baixa
            pedido.setStatus(StatusPedido.EM_PREPARACAO);
            pedidoRepository.save(pedido);

            log.info("Pedido {} atualizado para EM_PREPARACAO com sucesso via webhook!", pedidoId);

        } catch (Exception e) {
            log.error("Erro ao processar webhook do pagamento ID {}: {}", paymentId, e.getMessage(), e);
            throw new RuntimeException("Erro ao processar notificação de pagamento", e);
        }
    }
}