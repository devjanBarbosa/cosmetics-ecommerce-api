package com.ledacosmeticos.api.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MercadoPagoWebhookDTO(
    String action,
    String type,
    DataDTO data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DataDTO(String id) {}
}