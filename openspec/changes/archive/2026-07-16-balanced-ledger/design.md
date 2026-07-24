## Context

O Finsight modela movimentações em dois níveis: `Operation` (Kind: `TRANSACTION`/`PAYMENT`/`TRANSFER`) agrupa 1..N `Transaction` (Type: `EXPENSE`/`INCOME`/`ADJUSTMENT`; Target: `ACCOUNT`/`CREDIT_CARD`). O sinal do impacto vive em `signedImpact()` (`core/model/extension/Transaction.kt`). Despesa, receita e compra no cartão são **perna única** — a contrapartida (a categoria) é conceitual, não existe como lançamento. Só transferência e pagamento de fatura têm duas pernas. Consequências estruturais:

- Não existe invariante `Σ = 0`: a integridade depende de cada use case criar as pernas certas.
- `ADJUSTMENT` é um lançamento sem contrapartida, com `amount` = diferença; relatórios o tratam como exceção.
- `CalculateInvoiceUseCase` usa `-signedImpact()` (sinal invertido ad-hoc) para o lado do cartão.
- Não há representação para reembolso/estorno.

O roadmap declarado (câmbio, múltiplas moedas, investimentos, contas de terceiros) exige partidas dobradas plenas. Esta mudança estabelece a fundação; reembolso, FX e investimentos são mudanças posteriores construídas sobre ela.

## Goals / Non-Goals

**Goals:**
- Plano de contas unificado (`Account` com `type`) do qual conta, cartão e categoria são projeções.
- Toda operação como conjunto de `Entry` assinadas com `currency`, com invariante `Σ = 0` por moeda validada na escrita.
- Convenção débito-positivo interna, invisível ao usuário.
- Tipo de operação (despesa/receita/transferência/pagamento) **derivado** dos tipos de conta.
- Saldo de conta, gasto por categoria e patrimônio líquido a partir de um único mecanismo (`Σ entries`).
- Migração Room que preserva todos os saldos existentes e sintetiza contrapartidas.
- Paridade funcional e visual: nenhuma mudança de fluxo perceptível ao usuário nesta fase.

**Non-Goals:**
- UI de reembolso/estorno (mudança posterior; o razão já a habilita).
- Câmbio/FX e conta de trading (mudança posterior).
- Investimentos, commodities, lotes e preços (mudança posterior).
- Expor o plano de contas cru ao usuário — a fachada de "categoria" e "cartão" é mantida.

## Decisions

### D1. Plano de contas unificado com `AccountType`
`Account { id, name, type, currency, ... }` com `type ∈ {ASSET, LIABILITY, INCOME, EXPENSE, EQUITY}`. Conta corrente/poupança/dinheiro/investimento/receber-de-terceiro = `ASSET`; cartão/empréstimo/pagar-a-terceiro = `LIABILITY`; categorias = `INCOME`/`EXPENSE`; reconciliação/saldo inicial = `EQUITY`.
- *Por quê*: unifica os conceitos hoje espalhados (conta, cartão, categoria, ajuste) num único substrato, do qual todo saldo e relatório derivam.
- *Alternativa considerada*: manter categoria como entidade separada e só endurecer os casos de duas pernas existentes (nível 1). Rejeitada: não dá a invariante universal e vira retrabalho quando FX/investimentos chegarem.

### D2. `Entry` assinada com moeda; `Σ = 0` por moeda
`Entry { operationId, accountId, amount: Long (menor unidade), currency }`. `Operation` passa a ser `{ id, date, entries: List<Entry>, ...metadados }`. Invariante: para cada moeda presente, a soma das `amount` das entries daquela moeda é zero — validada num único ponto de escrita.
- *Por quê `currency` já agora*: a invariante multi-moeda é `Σ = 0 por moeda`, não num escalar. Nascer com o campo evita reescrever o modelo de dados na mudança de FX. v1 usa só a moeda base.
- *Por quê inteiro (menor unidade)*: `Double` acumula erro de ponto flutuante e a invariante `Σ = 0` exige igualdade exata. Migrar de `Double` para inteiro em centavos elimina a classe de bug. (Ver Risco R3.)
- *Alternativa considerada*: manter `Double`. Rejeitada: `0.1 + 0.2 ≠ 0.3` quebra a invariante de forma intermitente.

### D3. Convenção débito-positivo, invertida na UI
`+` = débito, `−` = crédito. Débito aumenta `ASSET`/`EXPENSE`; crédito aumenta `LIABILITY`/`INCOME`/`EQUITY`. O saldo natural de uma conta é `Σ amount das suas entries`; para exibição, contas de natureza credora (`LIABILITY`/`INCOME`/`EQUITY`) têm o sinal invertido para lerem positivo.
- *Por quê*: é a convenção contábil padrão, fechada sob câmbio e investimentos, e reduz saldo/relatório a uma soma. A inversão fica isolada na borda de apresentação.
- *Alternativa considerada*: "delta ao saldo natural" por tipo de conta (sem débito/crédito formal). Rejeitada: espalha condicionais de sinal por tipo em toda leitura; não escala para trading.

### D4. `Operation.Kind` derivado, não persistido
O rótulo (despesa/receita/transferência/pagamento) é computado dos tipos das contas das entries: `ASSET→EXPENSE` = despesa; `ASSET→ASSET` = transferência; `ASSET→LIABILITY` = pagamento; `INCOME→ASSET` = receita.
- *Por quê*: elimina estado redundante que pode divergir das entries reais.
- *Trade-off*: leituras que hoje filtram por `Kind` passam a derivar; encapsular a derivação num único ponto (extensão/mapper) para não espalhar a regra.

### D5. Fachada de categoria e cartão preservada
A UI continua falando "categoria" e "cartão". Internamente, `Category` projeta uma `Account` `INCOME`/`EXPENSE` (mantendo a regra `isAccept` como coerência entre natureza da conta e sentido do lançamento) e `CreditCard`/`Invoice` projetam contas `LIABILITY` com o sub-razão de fatura.
- *Por quê*: usuários pensam em categoria, não em "conta de despesa". A fachada mantém a intuição sem vazar contabilidade.

### D6. Ajuste como operação balanceada contra `EQUITY`
Ajuste de saldo vira uma operação de duas pernas: a conta ajustada + a conta `EQUITY:Reconciliação`, somando zero. Mantém a idempotência por data+conta já existente (atualiza/deleta o ajuste do dia).
- *Por quê*: remove o caso especial de `ADJUSTMENT` dos relatórios — reconciliação passa a ser uma conta como outra qualquer.

## Risks / Trade-offs

- **[R1] Migração Room que precisa preservar todos os saldos]** → Sintetizar a contrapartida de cada lançamento de perna única (categoria → entry `INCOME`/`EXPENSE`; ajuste → entry `EQUITY`), promover categorias e cartões ao plano de contas, e verificar por teste que o saldo de cada conta pós-migração é idêntico ao pré-migração. Referência de padrão: `Migration3To4Test.kt` e skill `room-database` (migration safety). FKs `onDelete=CASCADE`/`SET_NULL` revisadas caso a caso.
- **[R2] Invariante `Σ = 0` rejeitando operações legadas ou construídas errado]** → Validar num único ponto de escrita, com erro tipado (Arrow `Either`); cobrir com testes de construção para cada tipo de operação (despesa, receita, transferência, pagamento, ajuste).
- **[R3] Ponto flutuante quebrando a igualdade exata]** → Migrar valores para inteiro na menor unidade da moeda (D2); a igualdade `Σ = 0` passa a ser exata.
- **[R4] Escopo grande tocando muitas features]** → Manter paridade funcional como critério: nenhuma mudança de fluxo de usuário; a fachada de categoria/cartão isola a UI da reestruturação. Sequenciar por camada (modelo → persistência+migração → use cases → leituras → UI).
- **[R5] Derivação de `Kind` espalhada]** (D4) → Concentrar a regra de derivação num único util/mapper, testado, consumido por todas as telas.

## Migration Plan

1. Introduzir `Account`/`AccountType` e `Entry` no modelo e na persistência (tabelas novas), coexistindo com o esquema atual.
2. Migração Room versionada: criar contas para cada categoria/cartão/conta existente; para cada `Transaction`, gerar as entries balanceadas (perna real + contrapartida sintetizada); ajuste → par com `EQUITY`.
3. Reescrever leituras (saldo/fatura/patrimônio) sobre entries; remover `signedImpact()` e o cálculo invertido de fatura.
4. Reescrever construções (despesa/receita/transferência/pagamento/ajuste/parcelas/recorrentes) como operações balanceadas validadas.
5. Teste de equivalência de saldos pré/pós e teste da invariante; só então remover as colunas/entidades legadas.

**Rollback**: a migração é forward-only (Room). Mitigação: backup do banco antes da migração e teste exaustivo de equivalência de saldos em base real antes do release.

## Open Questions

- Representação da moeda base: constante de projeto vs. configurável por usuário já nesta fase (mínimo: uma moeda base fixa, `currency` presente no schema).
- Sub-razão de fatura: a fatura continua como entidade própria projetando a conta `LIABILITY`, ou vira um agrupamento derivado por período sobre a conta do cartão? (Preferência inicial: manter `Invoice` como entidade/projeção para não mexer no ciclo open/close/pay/reopen nesta fase.)
- Contas de sistema (`EQUITY:Reconciliação`, `EQUITY:Saldo Inicial`): criadas sob demanda ou semeadas na migração?
