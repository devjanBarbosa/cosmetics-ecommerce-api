package com.ledacosmeticos.api.DTO;

import java.util.UUID;

import com.ledacosmeticos.api.Model.Categoria;
import com.ledacosmeticos.api.Model.TipoCategoria;

public record CategoriaResponseDTO(
    UUID id,
    String nome,
    TipoCategoria tipo,
    String urlImagem
) {
    public CategoriaResponseDTO(Categoria categoria) {
        this(
            categoria.getId(),
            categoria.getNome(),
            categoria.getTipo(),
            categoria.getUrlImagem()
        );
    }
}