package com.ledacosmeticos.api.DTO;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record ProdutoRequestDTO(
    @NotBlank(message = "O nome do produto é obrigatório")
    String nome,

    String descricao,

    @NotNull(message = "O preço é obrigatório")
    @Positive(message = "O preço deve ser maior que zero")
    BigDecimal preco,

    @NotNull(message = "A quantidade em estoque é obrigatória")
    @PositiveOrZero(message = "O estoque não pode ser negativo")
    Integer estoque,

    @NotNull(message = "O ID da categoria é obrigatório")
    UUID categoriaId,

    String urlImagem
) {}