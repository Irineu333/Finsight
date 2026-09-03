---
area: mcp
severity: medium
type: data
---

# Um título vazio é lido como silêncio, e a edição responde que foi aplicada

## Cenário

**DADO** um template recorrente chamado "Aluguel", classificado por uma categoria
**QUANDO** o agente chama `update_recurring(id, title: "")` para deixá-lo nomeado só pela categoria
**ENTÃO** a resposta diz que a edição foi aplicada, a atividade é registrada como `APPLIED`, e o
título continua "Aluguel"
**DEVERIA** apagar o título — ou recusar, dizendo que não sabe apagá-lo

## Mecânica

`ToolSupport.string()` devolve `null` para uma string em branco, então `""` e *a chave não veio*
chegam iguais em quem lê. Duas edições leem o título assim — `arguments.string("title") ?:
stored.<título>` — e nas duas o `?:` transforma "apague" em "mantenha".

A superfície já sabe distinguir as duas perguntas, e em dois lugares o faz. `arguments.names(…)`
existe exatamente para isso, e sua KDoc diz por quê: *"a tool that pre-fills from a template has to
tell 'say nothing, give me the template's' from 'this cycle genuinely has none'"*. `confirm_recurring`
lê o título com `names("title")` e **documenta** o título vazio como a forma de apagar um; e o
`update_recurring` lê a **categoria** com `names("category_id")` poucas linhas acima de ler o
título da forma antiga. As duas perguntas são a mesma, respondidas de dois jeitos no mesmo `call`.

A regra do domínio continua valendo do outro lado e já tem teste: um template sem título e sem
categoria é recusado por `TITLE_OR_CATEGORY_REQUIRED`. Não é o domínio que falta — é a chamada não
chegar até ele.

## Evidência

- `RecurringWriteTools` (`update_recurring`) — `title = arguments.string("title") ?: stored.title`
- `TransactionWriteTools` (`update_transaction`) — `title = arguments.string("title") ?: stored.title`
- `RecurringOperationTools` (`confirm_recurring`) — `val title = if (arguments.names("title"))
  arguments.string("title") else recurring.title`, e a KDoc que documenta o título vazio como a
  forma de apagar
- `RecurringWriteTools` — `stored.category?.takeUnless { arguments.names("category_id") }`, a
  distinção feita no mesmo `call` para o campo vizinho
- `JsonObject?.string()` e `JsonObject?.names()` (`mcp/tool/WriteSupport.kt`) — a leitura que
  colapsa vazio em ausente, e a que separa as duas

*Medido pela revisão que originou o registro: `{"id":2002,"title":""}` e `{"id":2001,"title":"   "}`
— ambos `APPLIED`, ambos com o título anterior intacto. Não foi remedido nesta revalidação; as
âncoras acima foram.*

## Consequência

A resposta afirma mais do que aconteceu, e o agente não tem como descobrir: `update_recurring`
responde *"Edited. Cycles already confirmed are untouched."* e `update_transaction` responde
*"Edited. Everything the call did not name kept the value it had."* — para um campo que a chamada
nomeou. Um template não tem como
voltar a ser nomeado só pela categoria, e a tool vizinha documenta o oposto para o mesmo campo.

## Sugestão

Ler o título com `names("title")` nas duas edições, como o `category_id` ao lado já é lido e como o
`confirm_recurring` já lê o seu. Não vinculante.
