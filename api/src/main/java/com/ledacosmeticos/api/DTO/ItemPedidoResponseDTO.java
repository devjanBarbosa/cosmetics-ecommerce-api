package com.ledacosmeticos.api.DTO;

import java.math.BigDecimal;

public record ItemPedidoResponseDTO(
    String nomeProduto,
    Integer quantidade,
    BigDecimal precoUnitario
) {}