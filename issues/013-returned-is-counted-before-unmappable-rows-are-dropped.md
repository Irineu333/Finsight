# 013 — `returned` é contado antes de as linhas não mapeáveis serem descartadas

**Área:** mcp · **Tipo:** correção (latente) · **Severidade:** baixa · **Status:** aberto
**Verificado em:** 2026-08-17, `feature/local-mcp-server` @ `cc6ca4ccf`

## O que está errado

`returned` é o tamanho da página *antes* do `mapNotNull`, então pode superar o número de lançamentos
que a resposta de fato carrega. A descrição da própria tool define o campo e a aritmética de
paginação em termos dele.

## Evidência

`feature/mcp/impl/.../tool/ListTransactionsTool.kt:183,189-195`

```kotlin
returned = page.returned,
…
transactions = page.items.mapNotNull { it.toAgentTransaction(…) },
```

`feature/mcp/impl/.../tool/TransactionListing.kt:69-79` — `val returned: Int get() = items.size`.

O mapper é documentado como retornando null (`surface/AgentTransactionMapper.kt:31-32`):

> Returns `null` when the perspective has no leg here, so a caller drops the item instead of
> failing on a read.

E a descrição enuncia o contrato (`ListTransactionsTool.kt:83-84`, `:114`):

> `matching` is how many postings the filter reaches and `returned` how many came back … the page
> after the last is `offset + returned`.

## Por que é latente

O `mapNotNull` não consegue descartar nada hoje:

- com uma perspectiva, a lista já foi filtrada para lançamentos que têm perna nela
  (`ListTransactionsTool.kt:162`), então `legUnder(perspective)` sempre encontra uma;
- sem perspectiva, `legUnder(null)` é `Transaction.primaryEntry` (`TransactionPerspective.kt:30-33`),
  null apenas para uma transação **sem nenhuma perna monetária** (`core/ledger/.../Transaction.kt:63-64`)
  — que nenhum caminho de escrita produz.

Ou seja, é uma inconsistência à espera de um quarto motivo para o mapper responder null, não um
defeito observável. Registrada porque os dois números deveriam descrever a mesma lista.

## Correção sugerida

Mapear primeiro, contar depois:

```kotlin
val mapped = page.items.mapNotNull { it.toAgentTransaction(…) }
…
returned = mapped.size,
transactions = mapped,
```

`hasMore`/`offset` continuam vindo de `page`, já que descrevem o corte subjacente.
