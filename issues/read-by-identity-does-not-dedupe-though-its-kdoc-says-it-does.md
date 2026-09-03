---
area: ledger
severity: low
type: data
---

# `readByIdentity` não deduplica, e sua KDoc afirma que sim

## Invariante

Toda identidade passada a `readByIdentity` é pedida exatamente uma vez — o que sua KDoc promete:
"the chunks partition the list, so nothing is read twice and nothing falls between two of them".

Hoje é falso para qualquer `Collection` com repetido que atravesse a fronteira de dois chunks.
`chunked(MAX_BOUND_IDENTITIES)` particiona por **posição**, não por valor.

## Mecânica

Dentro de um mesmo chunk não há dano: `IN (1,1)` devolve cada linha uma vez. Mas um id repetido
nos índices 899 e 900 cai em dois `IN` distintos, e `flatMap` concatena os dois resultados. Medido
com o schema do razão:

```
SELECT count(*) FROM entries WHERE transactionId IN (1,1);            -> 2   (as duas pernas)
SELECT count(*) FROM (… IN (1) UNION ALL … IN (1));                   -> 4   (em dobro)
```

Em `TransactionRepository.toDomain()` o `groupBy { it.transactionId }` entregaria àquela transação
todas as pernas em dobro. `isBalanced()` continua passando — 2 × 0 = 0 —, então nada recusa, e
qualquer consumidor que some `transaction.entries` reporta o dobro do dinheiro.

É latente porque os dois chamadores de produção deduplicam por construção, cada um por conta
própria: um monta os ids a partir de uma consulta por chave primária, o outro faz `ids.toSet()`
antes de chamar. A garantia está **escrita na função e implementada nos chamadores**, o receptor é
`Collection<Long>` e não `Set<Long>`, e a falha é invisível abaixo de 900 identidades.

## Evidência

- `readByIdentity()` (`core/ledger` — `database/BoundIdentities.kt`) — `chunked(MAX_BOUND_IDENTITIES)
  .flatMap { read(it) }`, e a KDoc que promete a partição por identidade
- `MAX_BOUND_IDENTITIES` — `900`, o tamanho do chunk e portanto onde a fronteira cai
- `TransactionRepository.toDomain()` — `.readByIdentity(entryDao::getByTransactionIds)`, seguido do
  `groupBy { it.transactionId }`
- `TransactionRepository.getExistingTransactionIds()` — `ids.toSet().readByIdentity(…)`, o chamador
  que se protege sozinho

## Consequência

Nada quebra hoje. O que existe é uma função `internal` cujo contrato o compilador não sustenta: o
próximo chamador que passar uma `List` com repetido — e a assinatura convida a isso — recebe
pernas em dobro sem nenhum sinal, porque o razão continua batendo.

## Sugestão

`toSet().chunked(…)`, ou estreitar o receptor para `Set<Long>` e deixar o compilador exigir o que a
KDoc promete. Não vinculante.
