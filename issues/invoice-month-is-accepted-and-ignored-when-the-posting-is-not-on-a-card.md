---
area: mcp
severity: medium
type: data
---

# `invoice_month` é aceito e ignorado quando o lançamento não está num cartão

## Cenário

**DADO** um lançamento numa conta
**QUANDO** o agente chama `update_transaction(id, invoice_month: "2026-04")` — ou move o lançamento
para uma conta e nomeia o mês de fatura na mesma chamada
**ENTÃO** a resposta é *"Edited. Everything the call did not name kept the value it had."*, e o mês
de fatura, que a chamada nomeou, não foi a lugar nenhum
**DEVERIA** recusar o argumento, dizendo que ele só existe para um lançamento em cartão

## Mecânica

`invoiceDueMonth` atravessa `TransactionForm.from` intacto — não há normalização dele ali. Quem o
ignora é `BuildTransactionUseCaseImpl`: no ramo da conta o `return@either` monta a `TransactionIntent`
com título, data, uma perna e a contra-perna, e nunca lê `form.invoiceDueMonth`. O campo é lido só
depois, no ramo do cartão.

Por isso a recusa que a tool instalou para os argumentos que o **formulário** descarta não alcança
este: o descarte não acontece no formulário. E a nota de sucesso, escrita para prometer que nada foi
perdido, é exatamente a frase errada para o caso.

## Evidência

- `TransactionWriteTools` (`update_transaction`) — `invoiceDueMonth = arguments.monthOrNull
  ("invoice_month") ?: …`, montado no form sem checar o destino
- `TransactionWriteTools` — `note = "Edited. Everything the call did not name kept the value it
  had."`, a resposta que segue
- `BuildTransactionUseCaseImpl.invoke()` — `if (form.target.isAccount) { … return@either
  TransactionIntent(…) }`, o ramo que retorna sem tocar em `form.invoiceDueMonth`
- `TransactionForm.from()` (`core/model` — `domain/model/form/TransactionForm.kt`) —
  `invoiceDueMonth = invoiceDueMonth`, sem normalização
- `TransactionWriteTools` — `"invoice_month" to text("Move a card posting to the invoice falling
  due in this month, as \`2026-04\`.")`, a descrição que a tool oferece

## Consequência

O agente pede uma coisa, é informado de que tudo que ele não nomeou foi preservado, e o que ele
nomeou é que sumiu. Não move dinheiro — o lançamento numa conta não tem fatura — mas é a superfície
afirmando um resultado que não teve.

## Sugestão

Recusar `invoice_month` quando o destino resolvido não for um cartão, ao lado das recusas que a
mesma tool já faz. Não vinculante.
