package com.ledacosmeticos.api.Controller;

import com.ledacosmeticos.api.DTO.CategoriaRequestDTO;
import com.ledacosmeticos.api.DTO.CategoriaResponseDTO;
import com.ledacosmeticos.api.Model.TipoCategoria; 
import com.ledacosmeticos.api.Service.CategoryService;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*; 

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categorias")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @GetMapping@PostMapping
public ResponseEntity<CategoriaResponseDTO> criarCategoria(@Valid @RequestBody CategoriaRequestDTO dto) {
    CategoriaResponseDTO novaCategoria = categoryService.criar(dto);
    return ResponseEntity.status(HttpStatus.CREATED).body(novaCategoria);
}

@PutMapping("/{id}")
public ResponseEntity<CategoriaResponseDTO> atualizarCategoria(
        @PathVariable UUID id, 
        @Valid @RequestBody CategoriaRequestDTO dto) {
    CategoriaResponseDTO atualizada = categoryService.atualizar(id, dto);
    return ResponseEntity.ok(atualizada);
}

@GetMapping
public ResponseEntity<List<CategoriaResponseDTO>> listarCategorias(
        @RequestParam(required = false) TipoCategoria tipo) {
    return ResponseEntity.ok(categoryService.listarTodas(tipo));
}

    

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarCategoria(@PathVariable UUID id) {
        try {
            categoryService.deletar(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}