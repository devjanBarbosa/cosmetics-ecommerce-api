package com.ledacosmeticos.api.Controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/cep")
public class CepController {

    private static final Pattern CEP_VALIDO = Pattern.compile("\\d{8}");
    private static final String VIACEP_URL = "https://viacep.com.br/ws/{cep}/json/";

    private final RestTemplate restTemplate;

    public CepController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/{cep}")
    public ResponseEntity<String> buscarEndereco(@PathVariable String cep) {
        String cepSanitizado = cep.replaceAll("\\D", "");

        if (!CEP_VALIDO.matcher(cepSanitizado).matches()) {
            return ResponseEntity.badRequest().body("{\"erro\": \"CEP inválido\"}");
        }

        try {
            String body = restTemplate.getForObject(VIACEP_URL, String.class, cepSanitizado);
            return ResponseEntity.ok(body);
        } catch (RestClientException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("{\"erro\": \"Falha ao consultar o ViaCEP\"}");
        }
    }
}