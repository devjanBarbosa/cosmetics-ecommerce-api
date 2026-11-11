package com.ledacosmeticos.api.Service;

import com.ledacosmeticos.api.DTO.ProdutoRequestDTO;
import com.ledacosmeticos.api.DTO.ProdutoResponseDTO;
import com.ledacosmeticos.api.Model.Categoria;
import com.ledacosmeticos.api.Model.Produto;
import com.ledacosmeticos.api.Model.TipoCategoria;
import com.ledacosmeticos.api.Repository.CategoriaRepository;
import com.ledacosmeticos.api.Repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.data.jpa.domain.Specification; // <-- NOVA IMPORTAÇÃO
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProdutoService { 

    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;
    private final ProdutoSpecification produtoSpecification; 
    
    public List<Produto> listarParaAdmin(String nome, UUID categoriaId, Boolean ativo) {
        // 1. Pedimos ao nosso construtor para criar a query com os filtros recebidos
        Specification<Produto> spec = produtoSpecification.findWithFilters(nome, categoriaId, ativo);
        
        // 2. O repositório agora sabe como executar esta query programática
        return produtoRepository.findAll(spec);
    }
    
    // ... (resto do seu serviço)
    
    // ATUALIZADO: Agora só lista produtos ativos
     public List<Produto> listarTodos(UUID categoriaId, String sortBy, TipoCategoria tipo) {
        if (categoriaId != null) {
            return produtoRepository.findByCategoriaIdAndAtivoTrue(categoriaId);
        }
        if (tipo != null) {
            return produtoRepository.findByCategoriaTipoAndAtivoTrue(tipo);
        }
        return produtoRepository.findByAtivoTrue();
    }

    @Transactional
public ProdutoResponseDTO cadastrar(ProdutoRequestDTO dto) {
    Categoria categoria = categoriaRepository.findById(dto.categoriaId())
            .orElseThrow(() -> new RuntimeException("Categoria não encontrada com ID: " + dto.categoriaId()));

    // 2. Mapear os dados do DTO para a nova entidade Produto
    Produto produto = new Produto();
    produto.setNome(dto.nome());
    produto.setDescricao(dto.descricao());
    produto.setPreco(dto.preco());
    produto.setEstoque(dto.estoque());
    produto.setUrlImagem(dto.urlImagem());
    produto.setCategoria(categoria);
    produto.setAtivo(true); 
    Produto produtoSalvo = produtoRepository.save(produto);

    // 4. Converter e retornar o DTO de resposta
    return new ProdutoResponseDTO(produtoSalvo);
}
    
    public List<Produto> pesquisarPorNome(String nome) {
        return produtoRepository.findByNomeContainingIgnoreCaseAndAtivoTrue(nome);
    }

    public Optional<Produto> buscarPorId(UUID id) {
        return produtoRepository.findById(id);
    }

    @Transactional
public ProdutoResponseDTO atualizar(UUID id, ProdutoRequestDTO dto) {
    Produto produtoExistente = produtoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Produto não encontrado com ID: " + id));

    Categoria categoria = categoriaRepository.findById(dto.categoriaId())
            .orElseThrow(() -> new RuntimeException("Categoria não encontrada com ID: " + dto.categoriaId()));

    produtoExistente.setNome(dto.nome());
    produtoExistente.setDescricao(dto.descricao());
    produtoExistente.setPreco(dto.preco());
    produtoExistente.setUrlImagem(dto.urlImagem());
    produtoExistente.setEstoque(dto.estoque());
    produtoExistente.setCategoria(categoria);

    Produto produtoAtualizado = produtoRepository.save(produtoExistente);
    return new ProdutoResponseDTO(produtoAtualizado);
}
    @Transactional
    public void inativar(UUID id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado para inativar!"));
        
        produto.setAtivo(false);
        produtoRepository.save(produto);
    }
}