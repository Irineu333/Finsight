---
area: mcp
severity: low
type: ux
verdict: fixed
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

## Desfecho

**Causa real** — a descrita, e as duas âncoras conferem:
`TransactionWriteTools.kt:461-462` (`namedCard?.let { … } ?: storedCard.takeIf { namedAccount ==
null }`) com a guarda em `:477-485`, e `RecurringWriteTools.kt:249-250` com a guarda em `:264-272`.
A forma certa vizinha também: `TransactionWriteTools.kt:538-550` e `RecurringWriteTools.kt:299-311`,
a recusa da categoria carregada, com o comentário que diz que ali o errado *"is not an argument but
a consequence the caller did not ask for"*.

A variante do `type` é maior do que o registro conta: `update_recurring` carrega o `type`
(`RecurringWriteTools.kt:256-258`), mas `update_transaction` **também**
(`TransactionWriteTools.kt:467-469`, `?: stored.storedType()`). Os dois eixos são carregados nas
duas ferramentas, e a redação antiga afirmava os dois como argumentos.

**Mudança** — a mesma em cada ferramenta: a guarda passa a escolher entre duas frases, pela origem
do cartão, e nenhuma das duas nomeia `type`.

- cartão dado: *"A card takes expenses only, and this edit leaves the posting an income: give
  `account_id` and not `card_id`."* — a metade acionável intacta, e o `card_id` só aparece onde ele
  existe.
- cartão carregado: *"The posting sits on \"Cartão\" and this edit leaves it an income, which a
  card cannot hold: a card takes expenses only. Give the `account_id` of the account the money came
  into."* — o que o lançamento traz, por que a direção pedida não o mantém, e o que dar; nada a
  remover.

`update_recurring` recebe as mesmas duas, com *template* no lugar de *posting* e *"charged to"* no
lugar de *"sits on"*.

*"this edit leaves … an income"* é verdadeiro quando o `type` foi dado e quando foi carregado, que
é como a frase deixa de mentir sobre o segundo eixo sem precisar ramificar de novo.

**Prova** — teste novo, `ARefusalNamesOnlyWhatTheCallGaveTest`, dois casos pelo protocolo: um
lançamento criado num cartão e depois `update_transaction {"id":X,"type":"income"}`, e um template
criado num cartão e depois `update_recurring {"id":X,"type":"income"}` — exatamente o cenário do
registro. Assere que a razão **não** contém `card_id`, que contém `account_id`, e que nomeia o
cartão ("Cartão").

Vermelho antes da correção, nos dois: *"the refusal asks for a `card_id` back, and the call gave
none: A card takes expenses only: with `type` income, give `account_id` and not `card_id`."* Verde
depois. O teste que já existia sobre o caso declarado
(`RegistrationFamilyOverTheProtocolTest`, `moving an income onto a card is refused for the card, not
for a missing account`) segue verde: aquele dá `card_id`, e naquele ramo a frase continua nomeando-o.
Suíte do módulo: 280 testes, 0 falhas (`./gradlew :feature:mcp:impl:jvmTest`).

**Commit** — `Fix(Mcp): close the eleven defects the surface sweep had open`
