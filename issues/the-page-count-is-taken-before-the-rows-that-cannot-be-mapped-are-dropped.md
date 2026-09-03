---
area: mcp
severity: low
type: data
---

# `returned` é contado antes de as linhas não mapeáveis serem descartadas

## Invariante

`returned` conta os lançamentos que a resposta carrega.

Hoje é falso: `returned` é o tamanho da página **antes** do `mapNotNull` que monta
`transactions`, então os dois números descrevem listas que podem divergir — e a aritmética de
paginação que a própria tool documenta é definida sobre o primeiro.

## Mecânica

`TransactionListing.returned` é `items.size`, a página crua. A resposta o copia direto e só então
mapeia cada linha, com um mapper cuja KDoc declara `null` como resposta legítima —
"a caller drops the item instead of failing on a read". Descartar é o contrato; não recontar é o
defeito.

Hoje o `mapNotNull` não consegue descartar nada, e é por isso que isto é latente e não ativo: com
perspectiva, a lista já foi filtrada para lançamentos que têm perna nela, então `legUnder` sempre
encontra uma; sem perspectiva, `legUnder(null)` é `Transaction.primaryEntry`, `null` apenas para
uma transação sem nenhuma perna monetária — que nenhum caminho de escrita produz. É uma
inconsistência à espera de um terceiro motivo para o mapper responder `null`.

## Evidência

- `ListTransactionsTool` — `returned = page.returned`, e `transactions = page.items.mapNotNull {
  it.toAgentTransaction(…) }` quatro linhas abaixo
- `TransactionListing.returned` — `get() = items.size`
- `Transaction.toAgentTransaction()` (`mcp/surface/AgentTransactionMapper.kt`) — `val leg =
  legUnder(accountId) ?: return null`, e a KDoc que declara o contrato
- `ListTransactionsTool` — o filtro `perspective == null || it.entries.any { leg -> leg.account.id
  == perspective }`, que é o que hoje impede o descarte
- `Transaction.legUnder()` (`core/ui` — `model/model/TransactionPerspective.kt`) — `null ->
  primaryEntry`
- descrição da própria tool — "`matching` is how many postings the filter reaches and `returned`
  how many came back", e "the page after the last is `offset + returned`"

## Consequência

Se o mapper passar a descartar, um agente paginando por `offset + returned` pula lançamentos, sem
nada na resposta que denuncie o salto. O dano é o silêncio: os dois números continuariam parecendo
consistentes.

## Sugestão

Mapear primeiro e contar depois — `returned = mapped.size`, `transactions = mapped`. `hasMore` e
`offset` continuam vindo de `page`, que descreve o corte subjacente. Não vinculante.
