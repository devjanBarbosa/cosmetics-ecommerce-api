package com.ledacosmeticos.api.Service;

import com.ledacosmeticos.api.DTO.ItemPedidoResponseDTO;
import com.ledacosmeticos.api.DTO.PedidoRequestDTO;
import com.ledacosmeticos.api.DTO.PedidoResponseDTO;
import com.ledacosmeticos.api.Exception.Excecoes.EstoqueInsuficiente;
import com.ledacosmeticos.api.Exception.Excecoes.GatewayIndisponivel;
import com.ledacosmeticos.api.Exception.Excecoes.RecursoNaoEncontrado;
import com.ledacosmeticos.api.Model.*;
import com.ledacosmeticos.api.Repository.ClienteRepository;
import com.ledacosmeticos.api.Repository.PedidoRepository;
import com.ledacosmeticos.api.Repository.ProdutoRepository;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PedidoService {

    private static final String PREFIXO_TESTE_CARGA = "pay_";

    private final PixService pixService;
    private final ConfiguracaoService configuracaoService;
    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;
    private final TransactionTemplate tx;
    private final boolean bypassTesteCarga;

    public PedidoService(PixService pixService,
                         ConfiguracaoService configuracaoService,
                         PedidoRepository pedidoRepository,
                         ProdutoRepository produtoRepository,
                         ClienteRepository clienteRepository,
                         TransactionTemplate tx,
                         // desligado por padrão: só ligue em ambiente de teste de carga
                         @Value("${app.payments.test-bypass:false}") boolean bypassTesteCarga) {
        this.pixService = pixService;
        this.configuracaoService = configuracaoService;
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.clienteRepository = clienteRepository;
        this.tx = tx;
        this.bypassTesteCarga = bypassTesteCarga;
    }

    // =====================================================================
    // CRIAÇÃO DO PEDIDO
    // Sem @Transactional no método: a chamada ao gateway fica FORA da transação,
    // então nenhuma conexão do pool fica presa esperando a API externa.
    //   Transação 1: cria pedido + reserva estoque (atômico)
    //   Fora de transação: gera PIX no Mercado Pago
    //   Transação 2: grava os dados do PIX
    // Se o gateway falhar, cancela o pedido e devolve o estoque (compensação).
    // =====================================================================
    public PedidoResponseDTO criar(PedidoRequestDTO dto) {
        Pedido criado = tx.execute(s -> criarPedidoReservandoEstoque(dto));
        UUID pedidoId = criado.getId();

        if (!"Pix".equalsIgnoreCase(dto.metodoPagamento())) {
            return tx.execute(s -> convertToDto(buscarOuFalhar(pedidoId)));
        }

        Payment pagamento;
        try {
            pagamento = pixService.criarCobrancaPix(criado);
        } catch (MPException | MPApiException e) {
            log.error("Falha ao gerar PIX do pedido {}. Cancelando e devolvendo estoque.", pedidoId, e);
            cancelar(pedidoId);
            throw new GatewayIndisponivel("Falha ao comunicar com o gateway de pagamento. Tente novamente.", e);
        }

        return tx.execute(s -> {
            Pedido pedido = buscarOuFalhar(pedidoId);
            var dados = pagamento.getPointOfInteraction().getTransactionData();
            pedido.setPixQrCodeBase64("data:image/png;base64," + dados.getQrCodeBase64());
            pedido.setPixCopiaECola(dados.getQrCode());
            pedido.setPixTransactionId(pagamento.getId().toString());
            return convertToDto(pedidoRepository.save(pedido));
        });
    }

    private Pedido criarPedidoReservandoEstoque(PedidoRequestDTO dto) {
        Cliente cliente = findOrCreateCliente(dto);

        Pedido pedido = new Pedido();
        pedido.setCliente(cliente);
        pedido.setDataCriacao(LocalDateTime.now());
        pedido.setStatus(StatusPedido.PENDENTE);
        pedido.setMetodoPagamento(dto.metodoPagamento());

        if ("Delivery".equalsIgnoreCase(dto.metodoEntrega())) {
            pedido.setTipoEntrega(TipoEntrega.ENTREGA_LOCAL);
            pedido.setEndereco(criarEndereco(dto));
        } else {
            pedido.setTipoEntrega(TipoEntrega.RETIRADA_NA_LOJA);
        }

        processarItens(pedido, dto.itens());
        calcularTotais(pedido);
        return pedidoRepository.save(pedido);
    }

    private Cliente findOrCreateCliente(PedidoRequestDTO dto) {
        return clienteRepository.findByWhatsapp(dto.whatsappCliente())
                .orElseGet(() -> {
                    Cliente novo = new Cliente();
                    novo.setNome(dto.nomeCliente());
                    novo.setWhatsapp(dto.whatsappCliente());
                    return clienteRepository.save(novo);
                });
    }

    private Endereco criarEndereco(PedidoRequestDTO dto) {
        Endereco e = new Endereco();
        e.setCep(dto.cep());
        e.setLogradouro(dto.endereco());
        e.setNumero(dto.numero());
        e.setComplemento(dto.complemento());
        e.setBairro(dto.bairro());
        return e;
    }

    /**
     * Reserva o estoque AQUI, uma única vez, com UPDATE condicional atômico
     * (estoque = estoque - n WHERE estoque >= n). Dois pedidos simultâneos não
     * conseguem vender além do estoque. Se qualquer item falhar, a transação
     * inteira sofre rollback e as reservas anteriores desfazem junto.
     */
      private void processarItens(Pedido pedido, List<PedidoRequestDTO.ItemDTO> itemDtos) {
        List<ItemPedido> itens = new ArrayList<>();
        for (PedidoRequestDTO.ItemDTO itemDto : itemDtos) {
            int quantidade = itemDto.quantidade();
            if (quantidade <= 0) {
                throw new IllegalArgumentException("Quantidade inválida para o produto " + itemDto.produtoId());
            }

            UUID prodId = UUID.fromString(itemDto.produtoId());

            // 1. FAZ O UPDATE PRIMEIRO (Se limpar o cache, não afeta entidades em memória)
            if (produtoRepository.baixarEstoque(prodId, quantidade) == 0) {
                throw new EstoqueInsuficiente("Estoque esgotado para o produto ID: " + prodId);
            }

            // 2. BUSCA O PRODUTO DEPOIS (Assim ele vem sempre no estado 'managed')
            Produto produto = produtoRepository.findById(prodId)
                    .orElseThrow(() -> new RecursoNaoEncontrado("Produto não encontrado com ID: " + prodId));

            ItemPedido item = new ItemPedido();
            item.setProduto(produto);
            item.setQuantidade(quantidade);
            item.setPrecoUnitario(produto.getPreco());
            item.setPedido(pedido);
            item.setId(new ItemPedidoId());
            itens.add(item);
        }
        pedido.setItens(itens);
    }
    
    private void calcularTotais(Pedido pedido) {
        BigDecimal subtotal = pedido.getItens().stream()
                .map(i -> i.getPrecoUnitario().multiply(BigDecimal.valueOf(i.getQuantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal taxa = BigDecimal.ZERO;
        if (pedido.getTipoEntrega() == TipoEntrega.ENTREGA_LOCAL) {
            taxa = new BigDecimal(String.valueOf(configuracaoService.getTaxaEntrega()))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // Ideal: migrar a entidade Pedido para BigDecimal e eliminar estes doubleValue().
        pedido.setSubtotal(subtotal.doubleValue());
        pedido.setTaxaEntrega(taxa.doubleValue());
        pedido.setValorTotal(subtotal.add(taxa).doubleValue());
    }

    // =====================================================================
    // WEBHOOK
    // Sem @Transactional no método: consulta ao Mercado Pago acontece antes de
    // abrir a transação. Idempotência por UPDATE condicional atômico
    // (PENDENTE -> EM_PREPARACAO): só UMA notificação "vence", mesmo concorrentes.
    // O estoque NÃO é mais baixado aqui (já foi reservado na criação).
    // =====================================================================
    public void processarNotificacaoPagamento(String paymentId) {
        if (bypassTesteCarga && paymentId != null && paymentId.startsWith(PREFIXO_TESTE_CARGA)) {
            // Formato do teste de carga: "pay_<uuid de um pedido PENDENTE existente>"
            UUID pedidoId = parseUuid(paymentId.substring(PREFIXO_TESTE_CARGA.length()));
            if (pedidoId == null) {
                log.warn("Bypass de teste com ID inválido: {}", paymentId);
                return;
            }
            liquidar(pedidoId, null); // sem conferência de valor no modo teste
            return;
        }

        Payment payment;
        try {
            payment = pixService.buscarPagamentoPorId(paymentId);
        } catch (MPException | MPApiException e) {
            // Falha transitória: 503 para o Mercado Pago reenviar com backoff
            log.error("Falha ao consultar pagamento {} no gateway", paymentId, e);
            throw new GatewayIndisponivel("Erro ao consultar pagamento no gateway", e);
        }

        if (payment == null) {
            log.warn("Nenhum pagamento retornado pelo Mercado Pago para o ID: {}", paymentId);
            return;
        }
        if (!"approved".equalsIgnoreCase(payment.getStatus())) {
            log.info("Pagamento {} com status '{}': nada a fazer.", paymentId, payment.getStatus());
            return;
        }

        UUID pedidoId = parseUuid(payment.getExternalReference());
        if (pedidoId == null) {
            // SEM fallback: nunca liquidar um pedido "qualquer". Log e segue (200, não adianta reenviar).
            log.error("Pagamento {} aprovado sem external_reference válido ('{}'). Requer conciliação manual.",
                    paymentId, payment.getExternalReference());
            return;
        }

        liquidar(pedidoId, payment.getTransactionAmount());
    }

    private void liquidar(UUID pedidoId, BigDecimal valorPago) {
        tx.executeWithoutResult(s -> {
            Pedido pedido = pedidoRepository.findById(pedidoId).orElse(null);
            if (pedido == null) {
                log.error("Pagamento aprovado para pedido inexistente {}. Requer conciliação.", pedidoId);
                return;
            }

            if (valorPago != null) {
                BigDecimal esperado = BigDecimal.valueOf(pedido.getValorTotal()).setScale(2, RoundingMode.HALF_UP);
                if (esperado.compareTo(valorPago.setScale(2, RoundingMode.HALF_UP)) != 0) {
                    log.error("Valor divergente no pedido {}: esperado {}, pago {}. Não liquidado.",
                            pedidoId, esperado, valorPago);
                    return;
                }
            }

            int atualizados = pedidoRepository.atualizarStatusSe(
                    pedidoId, StatusPedido.PENDENTE, StatusPedido.EM_PREPARACAO);

            if (atualizados == 0) {
                log.info("Pedido {} não está PENDENTE (já processado/cancelado). Notificação ignorada.", pedidoId);
                return;
            }
            log.info("Pedido {} liquidado com sucesso.", pedidoId);
        });
    }

    // =====================================================================
    // CANCELAMENTO / EXPIRAÇÃO: devolve o estoque reservado
    // =====================================================================
    public void cancelarPorExpiracao(UUID pedidoId) {
        if (cancelar(pedidoId)) {
            log.info("Pedido {} expirado: cancelado e estoque devolvido.", pedidoId);
        }
    }

    /** Só cancela se ainda estiver PENDENTE. Se o pagamento chegou antes, não faz nada. */
    private boolean cancelar(UUID pedidoId) {
        return Boolean.TRUE.equals(tx.execute(s -> {
            if (pedidoRepository.atualizarStatusSe(pedidoId, StatusPedido.PENDENTE, StatusPedido.CANCELADO) == 0) {
                return false;
            }
            Pedido pedido = buscarOuFalhar(pedidoId);
            for (ItemPedido item : pedido.getItens()) {
                produtoRepository.reporEstoque(item.getProduto().getId(), item.getQuantidade());
            }
            return true;
        }));
    }

    // =====================================================================
    // CONSULTAS
    // =====================================================================
    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarTodos() {
        // TODO: paginar (Pageable) e usar join fetch/EntityGraph para evitar N+1
        return pedidoRepository.findAll().stream().map(this::convertToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO buscarPorId(UUID id) {
        return convertToDto(buscarOuFalhar(id));
    }

    @Transactional
    public PedidoResponseDTO atualizarStatus(UUID id, StatusPedido novoStatus) {
        Pedido pedido = buscarOuFalhar(id);
        pedido.setStatus(novoStatus);
        return convertToDto(pedidoRepository.save(pedido));
    }

    // =====================================================================
    // AUXILIARES
    // =====================================================================
    private Pedido buscarOuFalhar(UUID id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontrado("Pedido não encontrado com ID: " + id));
    }

    private UUID parseUuid(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private PedidoResponseDTO convertToDto(Pedido pedido) {
        List<ItemPedidoResponseDTO> itensDto = pedido.getItens().stream()
                .map(i -> new ItemPedidoResponseDTO(i.getProduto().getNome(), i.getQuantidade(), i.getPrecoUnitario()))
                .collect(Collectors.toList());

        Cliente cliente = pedido.getCliente();
        Endereco end = pedido.getEndereco();

        return new PedidoResponseDTO(
                pedido.getId().toString(),
                pedido.getDataCriacao(),
                cliente != null ? cliente.getNome() : "Cliente não informado",
                cliente != null ? cliente.getWhatsapp() : "N/A",
                pedido.getStatus(),
                pedido.getSubtotal(),
                pedido.getTaxaEntrega(),
                pedido.getValorTotal(),
                itensDto,
                pedido.getTipoEntrega(),
                pedido.getMetodoPagamento(),
                end != null ? end.getCep() : null,
                end != null ? end.getLogradouro() : null,
                end != null ? end.getNumero() : null,
                end != null ? end.getComplemento() : null,
                end != null ? end.getBairro() : null,
                pedido.getPixCopiaECola(),
                pedido.getPixQrCodeBase64()
        );
    }
}