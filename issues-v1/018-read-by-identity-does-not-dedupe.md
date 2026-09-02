# 018 — `readByIdentity` não deduplica, e sua KDoc afirma que sim

**Área:** ledger · **Tipo:** robustez (latente) · **Criticidade:** baixa · **Status:** aberto
**Verificado em:** 2026-08-18, por uma revisão adversarial do commit `87246cbe9`

## O que está errado

`core/ledger/.../database/BoundIdentities.kt:19-27`

```kotlin
 * Every identity is asked exactly once: the chunks partition the list, so nothing is read twice
 * and nothing falls between two of them.
 */
internal suspend fun <T> Collection<Long>.readByIdentity(
    read: suspend (List<Long>) -> List<T>,
): List<T> = chunked(MAX_BOUND_IDENTITIES).flatMap { read(it) }
```

A afirmação é falsa para qualquer `Collection` com repetido. Dentro de um mesmo chunk não há dano —
`IN (1,1)` devolve a linha uma vez. Mas um id repetido que **atravesse a fronteira** de dois chunks é
pedido em dois statements e os resultados são concatenados. Verificado contra sqlite3 com o schema
real:

```
SELECT count(*) FROM entries WHERE transactionId IN (1,1);          -> 2
SELECT count(*) FROM (... IN (1) UNION ALL ... IN (1));             -> 4
```

## Cenário de falha

Um chamador passa ≥901 identidades com uma repetida nos índices 899 e 900. Em
`TransactionRepository.kt:150-152` o `groupBy { it.transactionId }` entrega àquela transação **todas as
pernas em dobro**. O `isBalanced()` continua passando, porque 2 × 0 = 0, então nada recusa — e
qualquer consumidor que some `transaction.entries` reporta o dobro do dinheiro.

## Por que é latente

Os dois chamadores atuais deduplicam por construção: `TransactionRepository.kt:150` monta os ids a
partir de uma consulta por chave primária, e `:180` faz `ids.toSet()`. A função é `internal` e tem
exatamente esses dois usos.

Mas a garantia está **escrita na função e implementada nos chamadores**, o receptor é
`Collection<Long>` e não `Set<Long>`, e a falha é invisível abaixo de 900 identidades — a pior forma
de deixar algo latente.

## Correção sugerida

`toSet().chunked(...)`, ou estreitar o receptor para `Set<Long>` e deixar o compilador exigir o que a
KDoc promete.
