---
area: mcp
severity: medium
type: data
verdict: fixed
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

## Desfecho

**Causa real** — a descrita, e a sugestão estava certa nas duas pontas. O que a correção acrescentou
foi descartar a dúvida sobre `update_transaction` precisar de tratamento diferente: **a regra
"título ou categoria" existe dos dois lados**, com donos distintos — `RecurringForm.kt:53`
(`ensure(title.isNotBlank() || category != null)` → `RecurringError.TITLE_OR_CATEGORY_REQUIRED`) e
`ValidateTransactionFormUseCaseImpl.kt:38` (`ensure(!form.title.isNullOrEmpty() || form.category
!= null)` → `BuildTransactionError.TitleOrCategoryRequired`). Logo o `DEVERIA` se resolve pela mesma
saída nas duas tools — *apagar* — e a recusa, quando o apagamento deixaria a operação sem nome,
já vinha do domínio sem nada a repetir na superfície. Nenhuma regra nova foi escrita.

O `?:` era o único ponto de perda: `ToolSupport.kt:105` (`content.takeIf { it.isNotBlank() }`) faz
`""`, `"   "` e *chave ausente* chegarem os três como `null`, e o `?:` reescrevia o título anterior
por cima. Como a coalescência é mais curta e mais natural que a leitura correta, o conserto de duas
linhas deixaria a armadilha de pé para o próximo campo — por isso a distinção virou um nome.

**Mudança** — `JsonObject?.stringOr(name, carried)` em `mcp/tool/WriteSupport.kt`, ao lado do
`names()` que ela aplica: `if (names(name)) string(name) else carried`. Passam a usá-la as três
tools que carregam um título do que editam — `update_recurring` (`RecurringWriteTools.kt`),
`update_transaction` (`TransactionWriteTools.kt`) e `confirm_recurring`
(`RecurringOperationTools.kt`, que já acertava à mão e agora diz o mesmo pelo mesmo nome). A leitura
de `category_id` nas duas edições não foi tocada. As descrições e o schema `title` das duas edições
passam a declarar o apagamento, que antes só `confirm_recurring` documentava: uma afordância que a
superfície não enuncia é uma que o agente não tem.

**Prova** — `EmptyStringsOverTheProtocolTest` (novo, em `feature/mcp/impl/src/jvmTest`), irmão de
`ExplicitNullsOverTheProtocolTest` e pelo mesmo motivo: a distinção entre vazio e ausente só existe
depois que os argumentos saem do fio. Seis testes, quatro vermelhos antes da mudança — o vazio e o
branco em `update_recurring`, o vazio em `update_transaction`, e a recusa que não vinha. O quarto
falhava exibindo exatamente a Consequência registrada: `"title":"Netflix"` intacto ao lado de
`"note":"Edited. Cycles already confirmed are untouched."`. Os dois testes de recusa cobrem a outra
metade (apagar o último nome é recusado, e o título fica de pé), e o sexto guarda a direção oposta —
um título que a chamada não nomeia continua mantido. `confirm_recurring` não foi duplicado aqui:
`OperationsFamilyOverTheProtocolTest.kt:262` já o cobria.

Rodados `:feature:mcp:impl:jvmTest` (263 testes) e `jvmTest` (suíte inteira), ambos verdes — numa
worktree isolada em HEAD contendo só esta mudança, porque a árvore compartilhada tinha o trabalho
em voo de outra correção (`invoice_month`) e dois vermelhos que não eram destes arquivos.

**Commit** — nenhum: a mudança ficou no working tree para revisão, a pedido.
