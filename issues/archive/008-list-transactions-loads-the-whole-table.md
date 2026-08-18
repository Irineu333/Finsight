# 008 — `list_transactions` carrega a tabela inteira (e uma query de entries por linha) a cada página

**Área:** mcp · **Tipo:** performance · **Criticidade:** média · **Status:** corrigida em 2026-08-18
**Verificado em:** 2026-08-17, `feature/local-mcp-server` @ `cc6ca4ccf`

## O que está errado

A listagem paginada lê **todos os lançamentos que o usuário já fez**, materializa cada um com suas
entries, filtra o mês em memória, ordena, e só então pega no máximo 200 linhas. Um agente que
percorre páginas paga esse custo uma vez por página.

## Evidência

`feature/mcp/impl/.../tool/ListTransactionsTool.kt:160-169`

```kotlin
val matching = transactionRepository.getAllTransactions()
    .filter { it.date.yearMonth == month }
    .filter { perspective == null || it.entries.any { leg -> leg.account.id == perspective } }
    .filter { category == null || it.matches(SpendingSubject.Categorized(category)) }
    .filter { label == null || it.entries.deriveTransactionLabel() == label }
    .inOrder(order)

val page = matching.pageOf(offset = offset, limit = limit)
```

`getAllTransactions()` não é uma query só:

`core/ledger/.../TransactionRepository.kt:138-141`

```kotlin
override suspend fun getAllTransactions(): List<Transaction> {
    val accounts = ledgerAccounts()
    return transactionDao.getAll().mapNotNull { it.toDomain(accounts) }
}
```

e `toDomain` (`:132-136`) dispara `entryDao.getByTransactionId(id)` **por transação**. O custo é
`1 (contas) + 1 (transações) + N (entries)` queries, com N do tamanho do ledger inteiro — para uma
página de 50.

O mês é o único filtro que poderia descer ao banco e não desce: `TransactionDao.kt:24-28` oferece
apenas `getAll()` / `observeAll()`, ambos sem filtro. As leituras com escopo de data existem do outro
lado da mesma tool — `entryRepository.totalsByDimensionByCurrency(startDate = …, endDate = …)` em
`ListTransactionsTool.kt:235-240`.

## Cenário de falha

Um ledger com 20 000 lançamentos. `list_transactions(month: "2026-03", limit: 50)` dispara ~20 002
queries e constrói 20 000 objetos `Transaction` com suas listas de `Entry`, para responder com 50. O
agente pede a página seguinte e tudo acontece de novo.

## Correção sugerida

Adicionar uma leitura de DAO com intervalo de datas (`WHERE date BETWEEN :from AND :to`) e uma
leitura de entries em lote (`WHERE transactionId IN (…)`, formato que o `EntryDao` já tem), e manter
os demais filtros em memória — `nature` e o corte por dimensão são derivados e não descem ao banco,
mas passam a rodar sobre um mês em vez de sobre todo o histórico.

Relacionado, no mesmo repositório: o item 23 de `docs/auditoria-bugs-2026-07.md` registra o espelho
disto no lado observável.
## Correção aplicada

O mês desceu para o banco e as entries passaram a ser lidas em lote:

- `TransactionDao.getBetween(startDate, endDate)` — `WHERE date BETWEEN :startDate AND :endDate`,
  inclusivo nas duas pontas, que é exatamente o que `yearMonth ==` significava. A data é gravada como
  texto `yyyy-MM-dd`, cuja ordem lexicográfica é a cronológica.
- `EntryDao.getByTransactionIds(...)` — as pernas de uma página numa leitura só, fatiada por
  `readByIdentity`/`MAX_BOUND_IDENTITIES`, a regra que a 007 estabeleceu medindo o driver real. Nenhuma
  segunda regra de fatiamento foi escrita.
- `TransactionRepository.getTransactionsBetween(...)`, e `getAllTransactions` passou a hidratar pelo
  mesmo caminho em lote — que é a **segunda metade** desta issue ("uma query de entries por linha"),
  não um item à parte.
- `nature` e o corte por dimensão continuam em memória, porque são derivados, mas agora rodam sobre um
  mês em vez de sobre todo o histórico.

Custo: a mesma pergunta sobre março passou de **3006 postagens hidratadas num ledger de 3006, contra
18 num de 18**, para **6 e 6** — constante no tamanho do ledger.

## As três redes, e a ordem em que foram feitas

Nada disto foi escrito por quem fez a correção, e essa separação é o ponto.

1. **A auditoria mediu o buraco antes.** O filtro de mês estava coberto de forma **vacuosa**: toda
   fixture semeava só março e toda chamada pedia março. Apagar `.filter { it.date.yearMonth == month }`
   por completo deixava a suíte em 1663 testes e zero falhas.
2. **`TransactionListingCutsTest`** (7 testes, commit anterior) fixou o corte nas fronteiras, o cartão,
   a categoria e o mês vazio — cada um provado por mutação.
3. **`TransactionListingCostTest`** foi escrito vermelho antes da correção, e afirma o requisito e não
   a solução: não nomeia método, query nem classe, só compara o custo do mesmo mês sobre dois ledgers
   de tamanhos muito diferentes. E exige que as duas respostas sejam a mesma página certa, para que
   não se possa passar fazendo menos e respondendo errado.

`TransactionPeriodReadTest` (7 testes, no `core:ledger`, pelo idioma do `LedgerFixture`) cobre o novo
`getBetween` no nível do DAO. Com a borda superior tornada exclusiva, **sete testes caem** — 3 no DAO
e 4 na tool.

## O que ficou fixado que antes não era

A ordem do DAO era **inobservável**: a tool reordena depois, então `ORDER BY date DESC, id DESC` não
tinha teste nem consequência. A leitura nova é contrato público de repositório e declara essa ordem,
então ela passou a ser afirmada — com fixture que semeia fora de sequência e com dois lançamentos no
mesmo dia, de modo que ordem de inserção, id sozinho e desempate ascendente produzam listas
diferentes. Nenhuma paginação em SQL foi introduzida.

## Fora de escopo, deliberadamente

A 013 não foi tocada: `returned` segue contado antes do `mapNotNull`, e nenhum teste novo cimenta
isso.
