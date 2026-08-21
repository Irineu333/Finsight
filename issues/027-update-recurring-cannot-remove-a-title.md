# 027 — `update_recurring` não consegue apagar um título, e a tool vizinha documenta o contrário

**Área:** mcp · **Tipo:** correção · **Criticidade:** baixa · **Status:** aberto
**Verificado em:** 2026-08-19, por uma revisão adversarial do commit `e58abf948`

## O que está errado

`ToolSupport.kt:105-106` devolve `null` para uma string em branco, e `RecurringWriteTools.kt` lê o
título como `arguments.string("title") ?: stored.title`. Então `title: ""` é lido como *a chamada não
disse nada*, o título antigo permanece, e a resposta diz que a edição foi aplicada.

O `confirm_recurring` — mesma família, mesma entidade — documenta exatamente o oposto na descrição e
no schema (`RecurringOperationTools.kt`, *"pass an empty title … for a cycle that genuinely had
neither"*) e implementa com `arguments.names("title")`. A superfície afirma as duas coisas.

O que torna o caso mais nítido: a [021](archive/021-update-recurring-stores-an-incoherent-template.md)
introduziu `arguments.names("category_id")` no `update_recurring` justamente para separar *não disse
nada* de *disse nenhuma* — e deixou o `title` na forma antiga poucas linhas abaixo. As duas perguntas
são a mesma, respondidas de duas maneiras no mesmo `call`.

## Cenário de falha

`update_recurring(id: 7, title: "")` num template chamado "Aluguel": resposta aplicada, log de
atividade registrando a edição, e o título continua "Aluguel". Um template não tem como voltar a ser
nomeado só pela categoria.

Medido pela revisão: `{"id":2002,"title":""}` e `{"id":2001,"title":"   "}` — ambos `APPLIED`, ambos
com o título anterior intacto.

## Correção sugerida

Ler o título com `names("title")`, como o `category_id` ao lado já é lido e como o
`confirm_recurring` já lê o seu. A regra do domínio continua valendo: um template sem título e sem
categoria é recusado por `TITLE_OR_CATEGORY_REQUIRED`, que é a resposta certa e já tem teste.

## Relacionado

A [024](024-update-transaction-still-discards-two-arguments.md) registra o mesmo `title: ""` em
`update_transaction`, ao lado do `invoice_month`. Aqui o caso é mais forte porque a tool vizinha
documenta o comportamento oposto para o mesmo campo, e porque a mesma chamada já distingue as duas
perguntas para a categoria.
