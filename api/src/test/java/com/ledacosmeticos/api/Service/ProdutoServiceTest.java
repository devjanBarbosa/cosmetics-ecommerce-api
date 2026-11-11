package com.ledacosmeticos.api.Service;

import com.ledacosmeticos.api.DTO.ProdutoRequestDTO;
import com.ledacosmeticos.api.DTO.ProdutoResponseDTO;
import com.ledacosmeticos.api.Model.Categoria;
import com.ledacosmeticos.api.Model.Produto;
import com.ledacosmeticos.api.Repository.CategoriaRepository;
import com.ledacosmeticos.api.Repository.ProdutoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @InjectMocks
    private ProdutoService produtoService;

    @Test
    @DisplayName("Deve cadastrar produto com sucesso quando a categoria existir")
    void deveCadastrarProdutoComSucesso() {
        UUID categoriaId = UUID.randomUUID();
        Categoria categoria = new Categoria();
        categoria.setId(categoriaId);
        categoria.setNome("Perfumaria");

        ProdutoRequestDTO dto = new ProdutoRequestDTO(
            "Perfume Floral",
            "Aroma marcante",
            new BigDecimal("89.90"),
            10,
            categoriaId,
            "http://imagem.com/foto.jpg"
        );

        when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoria));
        when(produtoRepository.save(any(Produto.class))).thenAnswer(i -> i.getArgument(0));

        ProdutoResponseDTO response = produtoService.cadastrar(dto);

        assertNotNull(response);
        assertEquals("Perfume Floral", response.nome());
        verify(produtoRepository, times(1)).save(any(Produto.class));
    }

    @Test
    @DisplayName("Deve lançar exceção quando a categoria não for encontrada")
    void deveLancarExcecaoQuandoCategoriaInexistente() {
        UUID categoriaId = UUID.randomUUID();
        ProdutoRequestDTO dto = new ProdutoRequestDTO(
            "Sabonete", "Corporal", new BigDecimal("5.00"), 20, categoriaId, null
        );

        when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> produtoService.cadastrar(dto));
        verify(produtoRepository, never()).save(any(Produto.class));
    }
}