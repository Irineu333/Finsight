# 008 — `list_transactions` carrega a tabela inteira (e uma query de entries por linha) a cada página

**Área:** mcp · **Tipo:** performance · **Severidade:** média · **Status:** aberto
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
