package com.ledacosmeticos.api.Service;

import com.ledacosmeticos.api.DTO.CategoriaRequestDTO;
import com.ledacosmeticos.api.DTO.CategoriaResponseDTO;
import com.ledacosmeticos.api.Model.Categoria;
import com.ledacosmeticos.api.Model.TipoCategoria;
import com.ledacosmeticos.api.Repository.CategoriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {

    private final CategoriaRepository categoriaRepository;

    public CategoryService(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> listarTodas(TipoCategoria tipo) {
        List<Categoria> categorias = (tipo != null) 
            ? categoriaRepository.findByTipo(tipo) 
            : categoriaRepository.findAll();

        return categorias.stream()
                .map(CategoriaResponseDTO::new)
                .toList();
    }

    @Transactional
    public CategoriaResponseDTO criar(CategoriaRequestDTO dto) {
        Categoria categoria = new Categoria();
        categoria.setNome(dto.nome());
        categoria.setTipo(dto.tipo());
        categoria.setUrlImagem(dto.urlImagem());

        Categoria salva = categoriaRepository.save(categoria);
        return new CategoriaResponseDTO(salva);
    }

    @Transactional
    public CategoriaResponseDTO atualizar(UUID id, CategoriaRequestDTO dto) {
        Categoria categoriaExistente = categoriaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada com ID: " + id));

        categoriaExistente.setNome(dto.nome());
        categoriaExistente.setTipo(dto.tipo());
        categoriaExistente.setUrlImagem(dto.urlImagem());

        Categoria atualizada = categoriaRepository.save(categoriaExistente);
        return new CategoriaResponseDTO(atualizada);
    }

    @Transactional
    public void deletar(UUID id) {
        if (!categoriaRepository.existsById(id)) {
            throw new RuntimeException("Categoria não encontrada com ID: " + id);
        }
        categoriaRepository.deleteById(id);
    }
}