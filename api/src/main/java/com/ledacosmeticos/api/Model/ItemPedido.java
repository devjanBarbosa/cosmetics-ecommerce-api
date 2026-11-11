package com.ledacosmeticos.api.Model;
import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity @Table(name = "itens_pedido") 
@Data
@NoArgsConstructor
public class ItemPedido {

    @EmbeddedId
    private ItemPedidoId id;
    
    @JsonBackReference @ManyToOne @MapsId("pedidoId") @JoinColumn(name = "pedido_id")

    private Pedido pedido;
    
    @ManyToOne @MapsId("produtoId") @JoinColumn(name = "produto_id")
    private Produto produto;
    private Integer quantidade;
    private BigDecimal precoUnitario;
}