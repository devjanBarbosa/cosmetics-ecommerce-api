package com.ledacosmeticos.api.DTO;

import com.ledacosmeticos.api.Model.TipoCategoria;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoriaRequestDTO(
    @NotBlank(message = "O nome da categoria é obrigatório")
    @Size(min = 2, max = 50, message = "O nome deve ter entre 2 e 50 caracteres")
    String nome,

    @NotNull(message = "O tipo da categoria é obrigatório")
    TipoCategoria tipo,

    String urlImagem
) {}