---
area: mcp
severity: low
type: data
verdict: fixed
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

## Desfecho

**Causa real** — a descrita, e conferida no disco: `ListTransactionsTool.kt:185` copiava
`returned = page.returned` — que é `items.size` do corte cru (`TransactionListing.kt:74`) — e só
quatro linhas abaixo montava `transactions` com `mapNotNull`. Duas listas, um número. Latente como
o relato diz: `Transaction.toAgentTransaction` só responde `null` quando `legUnder` responde
`null` (`AgentTransactionMapper.kt:52`), e hoje isso exige uma transação sem nenhuma perna
monetária, que nenhum caminho de escrita produz.

Uma segunda metade apareceu na correção e não estava no relato: **a aritmética de paginação não
sobrevive ao descarte por nenhum dos dois números**. Com o cru, o agente é informado de um total
que não recebeu; com o mapeado, `offset + returned` volta atrás e reentrega linhas que a página
anterior já mostrou. O único passo que o descarte não move é o do próprio corte — a página é
`drop(offset).take(limit)`, então a próxima começa em `offset + limit`, com `has_more` dizendo se
ela existe.

**Mudança** — `ListTransactionsTool.kt`: a página é mapeada antes de ser contada (`val transactions
= page.items.mapNotNull { … }`), `returned = transactions.size` e `transactions = transactions`;
`offset` e `hasMore` continuam vindo do corte, porque `has_more` fala do que o filtro ainda guarda
*além* desta página — algo que uma linha descartada dentro dela não muda. A descrição do parâmetro
`offset` passou a definir a paginação sobre `limit`. `TransactionListing.kt` não precisou mudar:
`Page.returned` continua sendo o tamanho do corte, que é o que ele é.

**Prova** — teste novo em `TransactionListingTest`, `a row the mapper cannot read is dropped, and
the count is of what came back`: March semeada mais uma correção entre duas pernas nominais, sem
perna em conta nenhuma — `primaryEntry` é `null` e o mapper a descarta. Vermelho antes da
correção (`expected:<5> but was:<6>` em `returned`), verde depois; as outras nove do arquivo
seguem verdes. Rodado com
`./gradlew :feature:mcp:impl:jvmTest --tests "com.neoutils.finsight.mcp.TransactionListingTest"`.

**A segunda ocorrência, fechada junto** — `get_invoice` repetia a mesma forma em
`InvoiceTools.kt:233` e `:237`: `returned = page.returned` com
`statement = page.items.mapNotNull { … }`. Foi corrigida da mesma maneira — a lista é montada numa
`val` antes do `answer(...)` e `returned = statement.size` —, porque um defeito de classe corrigido
numa ocorrência só deixa a próxima nascer, que é a lição que o `issues/README.md` registra como
paga mais de uma vez. Os dois textos de paginação do arquivo (`:71`, em `list_invoices`, e `:187`,
em `get_invoice`) passaram a definir a próxima página sobre `limit`. Em `list_invoices` o número
nunca esteve errado — ele acompanha um `.map`, que não descarta —, e o texto mudou assim mesmo para
que as três listagens da superfície não ensinem duas aritméticas.

O terceiro ponto que a varredura alcançou é `InvoiceTools.kt:109`, `returned = page.returned` em
`list_invoices`: fica como está, com `.map` ao lado. É a forma certa quando nada pode ser
descartado, e trocá-la seria copiar a correção em vez de aplicar a regra.

**Commit** — `Fix(Mcp): close the eleven defects the surface sweep had open`
