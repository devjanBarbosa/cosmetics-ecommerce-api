package com.ledacosmeticos.api.DTO;

import com.ledacosmeticos.api.Model.Produto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProdutoResponseDTO(
    UUID id,
    String nome,
    String descricao,
    BigDecimal preco,
    Integer estoque,
    String nomeCategoria,
    String urlImagem,
    Boolean ativo
) {
    public ProdutoResponseDTO(Produto p) {
        this(
            p.getId(),
            p.getNome(),
            p.getDescricao(),
            p.getPreco(),
            p.getEstoque(),
            p.getCategoria() != null ? p.getCategoria().getNome() : null,
            p.getUrlImagem(),
            p.getAtivo()
        );
    }
}