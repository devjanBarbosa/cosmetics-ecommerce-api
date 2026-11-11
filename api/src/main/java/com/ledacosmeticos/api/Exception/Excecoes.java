package com.ledacosmeticos.api.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Exceções de domínio: cada uma já carrega o status HTTP correto (404, 409, 503). */
public final class Excecoes {

    private Excecoes() {}

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class RecursoNaoEncontrado extends RuntimeException {
        public RecursoNaoEncontrado(String mensagem) { super(mensagem); }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class EstoqueInsuficiente extends RuntimeException {
        public EstoqueInsuficiente(String produto) {
            super("Estoque insuficiente para o produto: " + produto);
        }
    }

    /** Falha transitória do gateway: 503 faz o Mercado Pago reenviar o webhook (backoff). */
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public static class GatewayIndisponivel extends RuntimeException {
        public GatewayIndisponivel(String mensagem, Throwable causa) { super(mensagem, causa); }
    }
}