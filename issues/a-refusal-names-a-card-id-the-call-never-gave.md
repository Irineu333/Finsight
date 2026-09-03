---
area: mcp
severity: low
type: ux
---

# Uma recusa manda não dar um `card_id` que a chamada não deu

## Cenário

**DADO** um lançamento que **já está** num cartão
**QUANDO** o agente chama `update_transaction(id, type: "income")`
**ENTÃO** a recusa é *"A card takes expenses only: with `type` income, give `account_id` and not
`card_id`."* — sobre um `card_id` que a chamada não nomeou
**DEVERIA** descrever o cartão carregado como consequência, não como argumento errado: a metade
acionável (*dar `account_id`*) está certa, a outra manda desfazer algo que ninguém fez

## Mecânica

A tool resolve o cartão como `namedCard ?: storedCard.takeIf { namedAccount == null }`, e a guarda
dispara sobre esse valor — que pode ter vindo inteiro do lançamento. A redação, porém, foi escrita
para o caso em que ele veio da chamada.

A forma certa existe no mesmo arquivo, poucas linhas abaixo: a recusa da **categoria carregada**
descreve o que o lançamento traz e por que o novo `type` não pode mantê-lo, sem mandar remover
argumento nenhum. As duas recusas têm a mesma origem — um valor carregado que a nova direção não
aceita — e só uma recebeu esse tratamento.

`update_recurring` copiou a recusa na forma antiga e acrescentou uma variante: como aquela tool
também **carrega** o `type`, a razão pode afirmar `type` income numa chamada que não deu `type`
nenhum — dois argumentos, nenhum dos dois dado como a frase os descreve.

## Evidência

- `TransactionWriteTools` (`update_transaction`) — `val card = namedCard?.let { … } ?: storedCard
  .takeIf { namedAccount == null }`, e a guarda `if (type.isIncome && card != null)` com a razão
  que nomeia `card_id`
- `TransactionWriteTools` — a recusa vizinha, `if (carriedCategory != null && !carriedCategory.type
  .isAccept(type))`, cuja razão começa por *"The posting is classified under …"*; o comentário
  acima dela diz que o errado ali "is not an argument but a consequence the caller did not ask for"
- `RecurringWriteTools` (`update_recurring`) — `val card = namedCard?.let { … } ?: stored.creditCard
  .takeIf { namedAccount == null }`, a mesma guarda e a mesma razão
- `RecurringWriteTools` — `val type = arguments.oneOf("type", …) ?: stored.type`, o carregamento que
  produz a variante

*Medido pela revisão que originou o registro: `{"id":X,"card_id":1}` sobre um template de receita
responde a frase acima, sem que `type` tenha sido dado. Não foi remedido nesta revalidação; as
âncoras acima foram.*

## Consequência

O agente lê uma instrução que não tem como cumprir — não há `card_id` a remover — ao lado de uma
que tem. Nada é gravado errado; o custo é a recusa ensinar metade de uma regra falsa, e ser a
única das cinco recusas de direção da superfície a fazê-lo.

## Sugestão

Redigir as duas recusas como a da categoria carregada já é redigida: dizer o que o lançamento (ou o
template) traz, e que a direção pedida não pode mantê-lo. Não vinculante.
