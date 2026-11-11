// Uso:
//   k6 run -e PRODUTO_ID=<uuid> -e N_PEDIDOS=1500 ensaio-concorrencia-pix.js
// Pré-requisitos:
//   - app com app.payments.test-bypass=true e app.security.hmac-test-bypass=true (só em dev)
//   - produto com estoque >= N_PEDIDOS (cada pedido reserva 1 unidade na criação)
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUTO_ID = __ENV.PRODUTO_ID;
const N_PEDIDOS = Number(__ENV.N_PEDIDOS || 1500);
const JSON_HEADERS = { 'Content-Type': 'application/json' };
const LOTE = 20;

if (!PRODUTO_ID) {
  throw new Error('Informe o produto: k6 run -e PRODUTO_ID=<uuid> ensaio-concorrencia-pix.js');
}

export const options = {
  setupTimeout: '300s',
  teardownTimeout: '180s',
  stages: [
    { duration: '30s', target: 200 },  // Aquece
    { duration: '30s', target: 500 },  // Teste de carga normal
    { duration: '30s', target: 1000 }, // Começa a stressar
    { duration: '30s', target: 2000 }, // Ponto de quebra provável
    { duration: '30s', target: 5000 }, // Caos absoluto
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    // só o webhook entra nos thresholds (setup e teardown ficam de fora)
    'http_req_failed{name:webhook}': ['rate<0.01'],
    'http_req_duration{name:webhook}': ['p(95)<500', 'p(99)<1000'],
  },
};

function pedidoPayload(i, run) {
  // Força exatos 11 dígitos (padrão celular BR) para evitar erro de @Size
  const celular = `219${String(run).substring(0, 4)}${String(i).padStart(4, '0')}`;
  
  return JSON.stringify({
    nomeCliente: `Teste Carga ${i}`,
    whatsappCliente: celular,
    metodoEntrega: 'Retirada',
    metodoPagamento: 'Dinheiro',
    cep: '00000000', 
    endereco: 'Rua Teste',
    numero: '123',
    complemento: 'N/A', 
    bairro: 'Teste',
    itens: [{ produtoId: PRODUTO_ID, quantidade: 1 }]
  });
}

export function setup() {
  const run = String(Date.now()).slice(-6);
  const ids = [];

  for (let inicio = 0; inicio < N_PEDIDOS; inicio += LOTE) {
    const reqs = [];
    for (let i = inicio; i < Math.min(inicio + LOTE, N_PEDIDOS); i++) {
      reqs.push(['POST', `${BASE}/api/pedidos`, pedidoPayload(i, run), { headers: JSON_HEADERS }]);
    }
    for (const res of http.batch(reqs)) {
      if (res.status !== 200 && res.status !== 201) {
        throw new Error(`Falha ao criar pedido: status=${res.status} body=${res.body}`);
      }
      ids.push(res.json('id'));
    }
  }
  console.log(`Setup: ${ids.length} pedidos PENDENTES criados.`);
  return { ids };
}

export default function (data) {
  // Há mais requisições do que pedidos: cada pedido recebe notificações repetidas,
  // muitas simultâneas. É isso que exercita a idempotência.
  const id = data.ids[exec.scenario.iterationInTest % data.ids.length];

  const res = http.post(
    `${BASE}/api/webhooks/mercadopago`,
    JSON.stringify({ action: 'payment.updated', type: 'payment', data: { id: `pay_${id}` } }),
    {
      headers: {
        ...JSON_HEADERS,
        'x-signature': 'ts=1710000000,v1=simulated_hash_for_testing',
      },
      tags: { name: 'webhook' },
    }
  );

  check(res, { 'webhook 200': (r) => r.status === 200 });
  sleep(0.1);
}

export function teardown(data) {
  const contagem = {};
  for (let inicio = 0; inicio < data.ids.length; inicio += 50) {
    const reqs = data.ids.slice(inicio, inicio + 50)
      .map((id) => ['GET', `${BASE}/api/pedidos/${id}`, null, { tags: { name: 'verificacao' } }]);
    for (const res of http.batch(reqs)) {
      const status = res.status === 200 ? res.json('status') : `HTTP_${res.status}`;
      contagem[status] = (contagem[status] || 0) + 1;
    }
  }
  console.log(`Pedidos criados: ${data.ids.length}`);
  console.log(`Status finais: ${JSON.stringify(contagem)}`);
  console.log('Esperado: todos EM_PREPARACAO (nenhum PENDENTE ou duplicado).');
}