package com.ledacosmeticos.api.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ledacosmeticos.api.Model.Pedido;
import com.ledacosmeticos.api.Model.StatusPedido;

public interface PedidoRepository extends JpaRepository<Pedido, UUID> {

    // --- QUERIES OTIMIZADAS PARA BUSCA DE PEDIDOS ---
    @Query("SELECT p FROM Pedido p JOIN FETCH p.itens i JOIN FETCH i.produto JOIN FETCH p.cliente WHERE p.id = :id")
    Optional<Pedido> findByIdWithItensAndProdutos(@Param("id") UUID id);

    @Query("SELECT DISTINCT p FROM Pedido p JOIN FETCH p.itens i JOIN FETCH i.produto JOIN FETCH p.cliente")
    List<Pedido> findAllWithItensAndProdutos();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Pedido p SET p.status = :novoStatus WHERE p.id = :id AND p.status = :statusAtual")
    int atualizarStatusSe(@Param("id") UUID id, @Param("statusAtual") StatusPedido statusAtual, @Param("novoStatus") StatusPedido novoStatus);

    List<Pedido> findByStatus(StatusPedido status);

    Optional<Pedido> findByPixTransactionId(String pixTransactionId);

    // --- QUERIES PARA O DASHBOARD (CORRIGIDAS) ---
    @Query("SELECT p.id, p.valorTotal FROM Pedido p WHERE p.status = 'ENTREGUE' AND p.dataCriacao >= :dataInicio")
    List<Object[]> findVendasConcluidasDesde(@Param("dataInicio") LocalDateTime dataInicio);

    @Query("SELECT new map(c.nome as nome, SUM(ip.precoUnitario * ip.quantidade) as totalVendido) " +
           "FROM Pedido p JOIN p.itens ip JOIN ip.produto pr JOIN pr.categoria c " +
           "WHERE p.status = 'ENTREGUE' AND p.dataCriacao >= :dataInicio " +
           "GROUP BY c.nome ORDER BY totalVendido DESC")
    List<Map<String, Object>> findVendasPorCategoriaDesde(@Param("dataInicio") LocalDateTime dataInicio);

    @Query("SELECT pr.nome, SUM(ip.quantidade) as quantidadeVendida, SUM(ip.precoUnitario * ip.quantidade) as faturamentoGerado " +
           "FROM Pedido p JOIN p.itens ip JOIN ip.produto pr " +
           "WHERE p.status = 'ENTREGUE' AND p.dataCriacao >= :dataInicio " +
           "GROUP BY pr.nome ORDER BY quantidadeVendida DESC")
    List<Object[]> findProdutosMaisVendidosDesde(@Param("dataInicio") LocalDateTime dataInicio);
    
    @Query("SELECT FUNCTION('date', p.dataCriacao) as dia, SUM(p.valorTotal) as total " +
           "FROM Pedido p " +
           "WHERE p.status = 'ENTREGUE' AND p.dataCriacao >= :dataInicio " +
           "GROUP BY dia ORDER BY dia ASC")
    List<Object[]> findVendasDiariasDesde(@Param("dataInicio") LocalDateTime dataInicio);
    
    @Query("SELECT p FROM Pedido p JOIN FETCH p.cliente WHERE p.status NOT IN ('ENTREGUE', 'CANCELADO') ORDER BY p.dataCriacao DESC LIMIT 5")
    List<Pedido> findTop5Recentes();
    
}
