package com.ledacosmeticos.api.Repository;

import com.ledacosmeticos.api.Model.Produto;
import com.ledacosmeticos.api.Model.TipoCategoria;
import org.springframework.data.jpa.repository.JpaRepository;
// --- NOVA IMPORTAÇÃO ---
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

// --- ALTERAÇÃO AQUI ---
public interface ProdutoRepository extends JpaRepository<Produto, UUID>, JpaSpecificationExecutor<Produto> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Produto p SET p.estoque = p.estoque - :quantidade WHERE p.id = :id AND p.estoque >= :quantidade")
    int baixarEstoque(@Param("id") UUID id, @Param("quantidade") int quantidade);

    // Devolve o item ao estoque em caso de cancelamento/falha
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Produto p SET p.estoque = p.estoque + :quantidade WHERE p.id = :id")
    int reporEstoque(@Param("id") UUID id, @Param("quantidade") int quantidade);

    List<Produto> findByAtivoTrue();
    List<Produto> findByCategoriaIdAndAtivoTrue(UUID categoriaId);
    List<Produto> findByNomeContainingIgnoreCaseAndAtivoTrue(String nome);
    List<Produto> findByCategoriaTipoAndAtivoTrue(TipoCategoria tipo);

       List<Produto> findTop5ByEstoqueGreaterThanOrderByEstoqueAsc(int minEstoque);

       
}